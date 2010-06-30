/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public abstract class OrderEnumeratorBase extends OrderEnumerator {
  private boolean myProductionOnly;
  private boolean myCompileOnly;
  private boolean myRuntimeOnly;
  private boolean myWithoutJdk;
  private boolean myWithoutLibraries;
  protected boolean myWithoutDepModules;
  private boolean myWithoutThisModuleContent;
  protected boolean myRecursively;
  private boolean myRecursivelyExportedOnly;
  private boolean myExportedOnly;
  private Condition<OrderEntry> myCondition;
  private List<OrderEnumerationHandler> myCustomHandlers;
  private ModulesProvider myModulesProvider;

  public OrderEnumeratorBase(@Nullable Module module, @NotNull Project project) {
    for (OrderEnumerationHandler handler : OrderEnumerationHandler.EP_NAME.getExtensions()) {
      if (handler.isApplicable(project) && (module == null || handler.isApplicable(module))) {
        if (myCustomHandlers == null) {
          myCustomHandlers = new SmartList<OrderEnumerationHandler>();
        }
        myCustomHandlers.add(handler);
      }
    }
  }

  @Override
  public OrderEnumerator productionOnly() {
    myProductionOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator compileOnly() {
    myCompileOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator runtimeOnly() {
    myRuntimeOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutSdk() {
    myWithoutJdk = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutLibraries() {
    myWithoutLibraries = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutDepModules() {
    myWithoutDepModules = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutModuleSourceEntries() {
    myWithoutThisModuleContent = true;
    return this;
  }

  @Override
  public OrderEnumerator recursively() {
    myRecursively = true;
    return this;
  }

  @Override
  public OrderEnumerator exportedOnly() {
    if (myRecursively) {
      myRecursivelyExportedOnly = true;
    }
    else {
      myExportedOnly = true;
    }
    return this;
  }

  @Override
  public OrderEnumerator satisfying(Condition<OrderEntry> condition) {
    myCondition = condition;
    return this;
  }

  @Override
  public OrderEnumerator using(@NotNull ModulesProvider provider) {
    myModulesProvider = provider;
    return this;
  }

  @Override
  public PathsList getPathsList() {
    final PathsList list = new PathsList();
    collectPaths(list);
    return list;
  }

  @Override
  public void collectPaths(final PathsList list) {
    list.addVirtualFiles(getClassesRoots());
  }

  @Override
  public PathsList getSourcePathsList() {
    final PathsList list = new PathsList();
    collectSourcePaths(list);
    return list;
  }

  @Override
  public void collectSourcePaths(PathsList list) {
    list.addVirtualFiles(getSourceRoots());
  }

  @Override
  public Collection<VirtualFile> getClassesRoots() {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    collectPaths(files, false);
    return files;
  }

  @Override
  public Collection<VirtualFile> getSourceRoots() {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    collectPaths(files, true);
    return files;
  }

  private void collectPaths(final List<VirtualFile> list, final boolean collectSources) {
    forEach(new Processor<OrderEntry>() {
      @Override
      public boolean process(OrderEntry orderEntry) {
        if (orderEntry instanceof ModuleSourceOrderEntry) {
          collectModulePaths(((ModuleSourceOrderEntry)orderEntry).getRootModel(), list, collectSources);
        }
        else if (orderEntry instanceof ModuleOrderEntry) {
          ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
          final Module module = moduleOrderEntry.getModule();
          if (module != null) {
            if (myCustomHandlers != null) {
              for (OrderEnumerationHandler handler : myCustomHandlers) {
                if (handler.addCustomOutput(moduleOrderEntry, myProductionOnly, list)) {
                  return true;
                }
              }
            }
            collectModulePaths(getRootModel(module), list, collectSources);
          }
        }
        else {
          Collections.addAll(list, orderEntry.getFiles(collectSources ? OrderRootType.SOURCES : OrderRootType.CLASSES));
        }
        return true;
      }
    });
  }

  private ModuleRootModel getRootModel(Module module) {
    if (myModulesProvider != null) {
      return myModulesProvider.getRootModel(module);
    }
    return ModuleRootManager.getInstance(module);
  }

  protected void processEntries(final ModuleRootModel rootModel,
                              Processor<OrderEntry> processor,
                              Set<Module> processed, boolean firstLevel) {
    if (processed != null && !processed.add(rootModel.getModule())) return;

    for (OrderEntry entry : rootModel.getOrderEntries()) {
      if (myWithoutJdk && entry instanceof JdkOrderEntry
          || myWithoutLibraries && entry instanceof LibraryOrderEntry
          || (myWithoutDepModules && !myRecursively) && entry instanceof ModuleOrderEntry
          || myWithoutThisModuleContent && entry instanceof ModuleSourceOrderEntry) continue;

      boolean exported = !(entry instanceof JdkOrderEntry);
      if (entry instanceof ExportableOrderEntry) {
        ExportableOrderEntry exportableEntry = (ExportableOrderEntry)entry;
        final DependencyScope scope = exportableEntry.getScope();
        if (myCompileOnly && !scope.isForProductionCompile() && !scope.isForTestCompile()) continue;
        if (myRuntimeOnly && !scope.isForProductionRuntime() && !scope.isForTestRuntime()) continue;
        if (myProductionOnly) {
          if (!scope.isForProductionCompile() && !scope.isForProductionRuntime()
            || myCompileOnly && !scope.isForProductionCompile()
            || myRuntimeOnly && !scope.isForProductionRuntime()) continue;
        }
        exported = exportableEntry.isExported();
      }
      if (!exported) {
        if (myExportedOnly) continue;
        if (myRecursivelyExportedOnly && !firstLevel) continue;
      }

      if (myCondition != null && !myCondition.value(entry)) continue;

      if (myRecursively && entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        final Module module = moduleOrderEntry.getModule();
        if (module != null) {
          boolean processRecursively = true;
          if (myCustomHandlers != null) {
            for (OrderEnumerationHandler handler : myCustomHandlers) {
              if (!handler.shouldProcessRecursively(moduleOrderEntry)) {
                processRecursively = false;
                break;
              }
            }
          }

          if (processRecursively) {
            processEntries(getRootModel(module), processor, processed, false);
            continue;
          }
        }
      }

      if (!processor.process(entry)) {
        return;
      }
    }
  }

  private void collectModulePaths(ModuleRootModel rootModel, List<VirtualFile> list, final boolean collectSources) {
    if (collectSources) {
      if (myProductionOnly) {
        ContentEntry[] contentEntries = rootModel.getContentEntries();
        for (ContentEntry contentEntry : contentEntries) {
          for (SourceFolder folder : contentEntry.getSourceFolders()) {
            VirtualFile root = folder.getFile();
            if (root != null && !folder.isTestSource()) {
              list.add(root);
            }
          }
        }
      }
      else {
        Collections.addAll(list, rootModel.getSourceRoots());
      }
    }
    else {
      final CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      if (extension != null) {
        VirtualFile testOutput = extension.getCompilerOutputPathForTests();
        if (myProductionOnly) {
          testOutput = null;
        }

        if (testOutput != null) {
          list.add(testOutput);
        }
        VirtualFile output = extension.getCompilerOutputPath();
        if (output != null && !output.equals(testOutput)) {
          list.add(output);
        }
      }
    }
  }

  @Override
  public abstract void forEach(@NotNull Processor<OrderEntry> processor);

  @Override
  public void forEachLibrary(@NotNull final Processor<Library> processor) {
    forEach(new Processor<OrderEntry>() {
      @Override
      public boolean process(OrderEntry orderEntry) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
          if (library != null) {
            return processor.process(library);
          }
        }
        return true;
      }
    });
  }

  @Override
  public <R> R process(@NotNull final RootPolicy<R> policy, final R initialValue) {
    final OrderEntryProcessor<R> processor = new OrderEntryProcessor<R>(policy, initialValue);
    forEach(processor);
    return processor.myValue;
  }

  private class OrderEntryProcessor<R> implements Processor<OrderEntry> {
    private R myValue;
    private final RootPolicy<R> myPolicy;

    public OrderEntryProcessor(RootPolicy<R> policy, R initialValue) {
      myPolicy = policy;
      myValue = initialValue;
    }

    @Override
    public boolean process(OrderEntry orderEntry) {
      myValue = orderEntry.accept(myPolicy, myValue);
      return true;
    }
  }
}
