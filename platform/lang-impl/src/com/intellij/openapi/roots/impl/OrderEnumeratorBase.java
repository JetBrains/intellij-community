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
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
abstract class OrderEnumeratorBase extends OrderEnumerator implements OrderEnumeratorSettings {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderEnumeratorBase");
  private boolean myProductionOnly;
  private boolean myCompileOnly;
  private boolean myRuntimeOnly;
  private boolean myWithoutJdk;
  private boolean myWithoutLibraries;
  protected boolean myWithoutDepModules;
  private boolean myWithoutModuleSourceEntries;
  protected boolean myRecursively;
  protected boolean myRecursivelyExportedOnly;
  private boolean myExportedOnly;
  private Condition<OrderEntry> myCondition;
  private final List<OrderEnumerationHandler> myCustomHandlers;
  protected ModulesProvider myModulesProvider;
  private OrderRootsCache myCache;

  public OrderEnumeratorBase(@Nullable Module module, @NotNull Project project, @Nullable OrderRootsCache cache) {
    myCache = cache;
    List<OrderEnumerationHandler> customHandlers = null;
    for (OrderEnumerationHandler handler : OrderEnumerationHandler.EP_NAME.getExtensions()) {
      if (handler.isApplicable(project) && (module == null || handler.isApplicable(module))) {
        if (customHandlers == null) {
          customHandlers = new SmartList<OrderEnumerationHandler>();
        }
        customHandlers.add(handler);
      }
    }
    this.myCustomHandlers = customHandlers == null ? Collections.<OrderEnumerationHandler>emptyList() : customHandlers;
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
    myWithoutModuleSourceEntries = true;
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

  @Override
  public OrderRootsEnumerator roots(@NotNull OrderRootType rootType) {
    return new OrderRootsEnumeratorImpl(this, rootType);
  }

  @Override
  public OrderRootsEnumerator roots(@NotNull NotNullFunction<OrderEntry, OrderRootType> rootTypeProvider) {
    return new OrderRootsEnumeratorImpl(this, rootTypeProvider);
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
    if (myProductionOnly) flags |= 1;
    flags <<= 1;
    if (myCompileOnly) flags |= 1;
    flags <<= 1;
    if (myRuntimeOnly) flags |= 1;
    flags <<= 1;
    if (myWithoutJdk) flags |= 1;
    flags <<= 1;
    if (myWithoutLibraries) flags |= 1;
    flags <<= 1;
    if (myWithoutDepModules) flags |= 1;
    flags <<= 1;
    if (myWithoutModuleSourceEntries) flags |= 1;
    flags <<= 1;
    if (myRecursively) flags |= 1;
    flags <<= 1;
    if (myRecursivelyExportedOnly) flags |= 1;
    flags <<= 1;
    if (myExportedOnly) flags |= 1;
    return flags;
  }

  protected void processEntries(final ModuleRootModel rootModel,
                                Processor<OrderEntry> processor,
                                Set<Module> processed, boolean firstLevel) {
    if (processed != null && !processed.add(rootModel.getModule())) return;

    for (OrderEntry entry : rootModel.getOrderEntries()) {
      if (myCondition != null && !myCondition.value(entry)) continue;

      if (myWithoutJdk && entry instanceof JdkOrderEntry) continue;
      if (myWithoutLibraries && entry instanceof LibraryOrderEntry) continue;
      if (myWithoutDepModules) {
        if (!myRecursively && entry instanceof ModuleOrderEntry) continue;
        if (entry instanceof ModuleSourceOrderEntry && !isRootModuleModel(((ModuleSourceOrderEntry)entry).getRootModel())) continue;
      }
      if (myWithoutModuleSourceEntries && entry instanceof ModuleSourceOrderEntry) continue;

      OrderEnumerationHandler.AddDependencyType shouldAdd = OrderEnumerationHandler.AddDependencyType.DEFAULT;
      for (OrderEnumerationHandler handler : myCustomHandlers) {
        shouldAdd = handler.shouldAddDependency(entry, this);
        if (shouldAdd != OrderEnumerationHandler.AddDependencyType.DEFAULT) break;
      }
      if (shouldAdd == OrderEnumerationHandler.AddDependencyType.DO_NOT_ADD) continue;

      boolean exported = !(entry instanceof JdkOrderEntry);

      if (entry instanceof ExportableOrderEntry) {
        ExportableOrderEntry exportableEntry = (ExportableOrderEntry)entry;
        if (shouldAdd == OrderEnumerationHandler.AddDependencyType.DEFAULT) {
          final DependencyScope scope = exportableEntry.getScope();
          if (myCompileOnly && !scope.isForProductionCompile() && !scope.isForTestCompile()) continue;
          if (myRuntimeOnly && !scope.isForProductionRuntime() && !scope.isForTestRuntime()) continue;
          if (myProductionOnly) {
            if (!scope.isForProductionCompile() && !scope.isForProductionRuntime()
                || myCompileOnly && !scope.isForProductionCompile()
                || myRuntimeOnly && !scope.isForProductionRuntime()) {
              continue;
            }
          }
        }
        exported = exportableEntry.isExported();
      }
      if (!exported) {
        if (myExportedOnly) continue;
        if (myRecursivelyExportedOnly && !firstLevel) continue;
      }

      if (myRecursively && entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        final Module module = moduleOrderEntry.getModule();
        if (module != null) {
          boolean processRecursively = true;
          for (OrderEnumerationHandler handler : myCustomHandlers) {
            if (!handler.shouldProcessRecursively(moduleOrderEntry)) {
              processRecursively = false;
              break;
            }
          }

          if (processRecursively) {
            processEntries(getRootModel(module), processor, processed, false);
            continue;
          }
        }
      }

      if (myWithoutDepModules && entry instanceof ModuleOrderEntry) continue;
      if (!processor.process(entry)) {
        return;
      }
    }
  }

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
  public void forEachModule(@NotNull final Processor<Module> processor) {
    forEach(new Processor<OrderEntry>() {
      @Override
      public boolean process(OrderEntry orderEntry) {
        if (myRecursively && orderEntry instanceof ModuleSourceOrderEntry) {
          final Module module = ((ModuleSourceOrderEntry)orderEntry).getRootModel().getModule();
          return processor.process(module);
        }
        else if (!myRecursively && orderEntry instanceof ModuleOrderEntry) {
          final Module module = ((ModuleOrderEntry)orderEntry).getModule();
          if (module != null) {
            return processor.process(module);
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

  boolean addCustomOutput(Module forModule, ModuleRootModel orderEntryRootModel, OrderRootType type, Collection<VirtualFile> result) {
    for (OrderEnumerationHandler handler : myCustomHandlers) {
      final List<String> urls = new ArrayList<String>();
      final boolean added =
        handler.addCustomOutput(forModule, orderEntryRootModel, type, this, urls);
      for (String url : urls) {
        ContainerUtil.addIfNotNull(VirtualFileManager.getInstance().findFileByUrl(url), result);
      }
      if (added) {
        return true;
      }
    }
    return false;
  }

  boolean addCustomOutputUrls(Module forModule, ModuleRootModel orderEntryRootModel, OrderRootType type, Collection<String> result) {
    for (OrderEnumerationHandler handler : myCustomHandlers) {
      if (handler.addCustomOutput(forModule, orderEntryRootModel, type, this, result)) {
        return true;
      }
    }
    return false;
  }

  void addAdditionalRoots(Module forModule, Collection<VirtualFile> result) {
    final List<String> urls = new ArrayList<String>();
    for (OrderEnumerationHandler handler : myCustomHandlers) {
      handler.addAdditionalRoots(forModule, this, urls);
    }
    for (String url : urls) {
      ContainerUtil.addIfNotNull(VirtualFileManager.getInstance().findFileByUrl(url), result);
    }
  }

  void addAdditionalRootsUrls(Module forModule, Collection<String> result) {
    for (OrderEnumerationHandler handler : myCustomHandlers) {
      handler.addAdditionalRoots(forModule, this, result);
    }
  }

  @Override
  public boolean isRuntimeOnly() {
    return myRuntimeOnly;
  }

  @Override
  public boolean isCompileOnly() {
    return myCompileOnly;
  }

  @Override
  public boolean isProductionOnly() {
    return myProductionOnly;
  }

  public boolean isRootModuleModel(@NotNull ModuleRootModel rootModel) {
    return false;
  }

  /**
   * Runs processor on each module that this enumerator was created on.
   *
   * @param processor processor
   */
  public abstract void processRootModules(@NotNull Processor<Module> processor);

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
