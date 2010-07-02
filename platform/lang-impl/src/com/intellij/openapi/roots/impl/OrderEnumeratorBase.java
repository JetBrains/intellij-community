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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
abstract class OrderEnumeratorBase extends OrderEnumerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderEnumeratorBase");
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
  private OrderRootsCache myCache;

  public OrderEnumeratorBase(@Nullable Module module, @NotNull Project project, @Nullable OrderRootsCache cache) {
    myCache = cache;
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
  public OrderRootsEnumerator classes() {
    return new OrderRootsEnumeratorImpl(this, OrderRootType.CLASSES);
  }

  @Override
  public OrderRootsEnumerator sources() {
    return new OrderRootsEnumeratorImpl(this, OrderRootType.SOURCES);
  }

  ModuleRootModel getRootModel(Module module) {
    if (myModulesProvider != null) {
      return myModulesProvider.getRootModel(module);
    }
    return ModuleRootManager.getInstance(module);
  }

  public OrderRootsCache getCache() {
    LOG.assertTrue(myCache != null, "Caching is not supported for ModifiableRootModel");
    LOG.assertTrue(myCondition == null, "Caching not supported for OrderEnumerator with 'satisfying(Condition)' option");
    LOG.assertTrue(myModulesProvider == null, "Caching not supported for OrderEnumerator with 'using(ModulesProvider)' option");
    return myCache;
  }

  public int getFlags() {
    int flags = 0;
    int i = 0;
    if (myProductionOnly)           flags |= 1 << (i++);
    if (myCompileOnly)              flags |= 1 << (i++);
    if (myRuntimeOnly)              flags |= 1 << (i++);
    if (myWithoutJdk)               flags |= 1 << (i++);
    if (myWithoutLibraries)         flags |= 1 << (i++);
    if (myWithoutDepModules)        flags |= 1 << (i++);
    if (myWithoutThisModuleContent) flags |= 1 << (i++);
    if (myRecursively)              flags |= 1 << (i++);
    if (myRecursivelyExportedOnly)  flags |= 1 << (i++);
    if (myExportedOnly)             flags |= 1 << i;
    return flags;
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

  public boolean isProductionOnly() {
    return myProductionOnly;
  }

  @Override
  public <R> R process(@NotNull final RootPolicy<R> policy, final R initialValue) {
    final OrderEntryProcessor<R> processor = new OrderEntryProcessor<R>(policy, initialValue);
    forEach(processor);
    return processor.myValue;
  }

  boolean addCustomOutput(ModuleOrderEntry moduleOrderEntry, Collection<VirtualFile> result) {
    if (myCustomHandlers != null) {
      for (OrderEnumerationHandler handler : myCustomHandlers) {
        final List<String> urls = new ArrayList<String>();
        final boolean added = handler.addCustomOutput(moduleOrderEntry, myProductionOnly, urls);
        for (String url : urls) {
          ContainerUtil.addIfNotNull(VirtualFileManager.getInstance().findFileByUrl(url), result);
        }
        if (added) {
          return true;
        }
      }
    }
    return false;
  }

  boolean addCustomOutputUrls(ModuleOrderEntry moduleOrderEntry, Collection<String> result) {
    if (myCustomHandlers != null) {
      for (OrderEnumerationHandler handler : myCustomHandlers) {
        if (handler.addCustomOutput(moduleOrderEntry, myProductionOnly, result)) {
          return true;
        }
      }
    }
    return false;
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
