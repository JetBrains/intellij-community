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
abstract class OrderEnumeratorBase extends OrderEnumerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderEnumeratorBase");

  protected final OrderEnumeratorSettings mySettings = new OrderEnumeratorSettings();
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

  @NotNull
  public OrderEnumeratorSettings getSettings() {
    return mySettings;
  }

  @Override
  public OrderEnumerator productionOnly() {
    mySettings.productionOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator compileOnly() {
    mySettings.compileOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator runtimeOnly() {
    mySettings.runtimeOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutSdk() {
    mySettings.withoutJdk = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutLibraries() {
    mySettings.withoutLibraries = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutDepModules() {
    mySettings.withoutDepModules = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutModuleSourceEntries() {
    mySettings.withoutModuleSourceEntries = true;
    return this;
  }

  @Override
  public OrderEnumerator recursively() {
    mySettings.recursively = true;
    return this;
  }

  @Override
  public OrderEnumerator exportedOnly() {
    if (mySettings.recursively) {
      mySettings.recursivelyExportedOnly = true;
    }
    else {
      mySettings.exportedOnly = true;
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

  public int getFlags() {
    return mySettings.getFlags();
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

  protected void processEntries(final ModuleRootModel rootModel,
                                Processor<OrderEntry> processor,
                                Set<Module> processed, boolean firstLevel) {
    if (processed != null && !processed.add(rootModel.getModule())) return;

    for (OrderEntry entry : rootModel.getOrderEntries()) {
      if (myCondition != null && !myCondition.value(entry)) continue;

      if (mySettings.withoutJdk && entry instanceof JdkOrderEntry) continue;
      if (mySettings.withoutLibraries && entry instanceof LibraryOrderEntry) continue;
      if (mySettings.withoutDepModules) {
        if (!mySettings.recursively && entry instanceof ModuleOrderEntry) continue;
        if (entry instanceof ModuleSourceOrderEntry && !isRootModuleModel(((ModuleSourceOrderEntry)entry).getRootModel())) continue;
      }
      if (mySettings.withoutModuleSourceEntries && entry instanceof ModuleSourceOrderEntry) continue;

      OrderEnumerationHandler.AddDependencyType shouldAdd = OrderEnumerationHandler.AddDependencyType.DEFAULT;
      for (OrderEnumerationHandler handler : myCustomHandlers) {
        shouldAdd = handler.shouldAddDependency(entry, mySettings);
        if (shouldAdd != OrderEnumerationHandler.AddDependencyType.DEFAULT) break;
      }
      if (shouldAdd == OrderEnumerationHandler.AddDependencyType.DO_NOT_ADD) continue;

      boolean exported = !(entry instanceof JdkOrderEntry);

      if (entry instanceof ExportableOrderEntry) {
        ExportableOrderEntry exportableEntry = (ExportableOrderEntry)entry;
        if (shouldAdd == OrderEnumerationHandler.AddDependencyType.DEFAULT) {
          final DependencyScope scope = exportableEntry.getScope();
          if (mySettings.compileOnly && !scope.isForProductionCompile() && !scope.isForTestCompile()) continue;
          if (mySettings.runtimeOnly && !scope.isForProductionRuntime() && !scope.isForTestRuntime()) continue;
          if (mySettings.productionOnly) {
            if (!scope.isForProductionCompile() && !scope.isForProductionRuntime()
                || mySettings.compileOnly && !scope.isForProductionCompile()
                || mySettings.runtimeOnly && !scope.isForProductionRuntime()) {
              continue;
            }
          }
        }
        exported = exportableEntry.isExported();
      }
      if (!exported) {
        if (mySettings.exportedOnly) continue;
        if (mySettings.recursivelyExportedOnly && !firstLevel) continue;
      }

      if (mySettings.recursively && entry instanceof ModuleOrderEntry) {
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

      if (mySettings.withoutDepModules && entry instanceof ModuleOrderEntry) continue;
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
        if (orderEntry instanceof ModuleSourceOrderEntry) {
          final Module module = ((ModuleSourceOrderEntry)orderEntry).getRootModel().getModule();
          return processor.process(module);
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
        handler.addCustomOutput(forModule, orderEntryRootModel, type, mySettings, urls);
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
      if (handler.addCustomOutput(forModule, orderEntryRootModel, type, mySettings, result)) {
        return true;
      }
    }
    return false;
  }

  void addAdditionalRoots(Module forModule, Collection<VirtualFile> result) {
    final List<String> urls = new ArrayList<String>();
    for (OrderEnumerationHandler handler : myCustomHandlers) {
      handler.addAdditionalRoots(forModule, mySettings, urls);
    }
    for (String url : urls) {
      ContainerUtil.addIfNotNull(VirtualFileManager.getInstance().findFileByUrl(url), result);
    }
  }

  void addAdditionalRootsUrls(Module forModule, Collection<String> result) {
    for (OrderEnumerationHandler handler : myCustomHandlers) {
      handler.addAdditionalRoots(forModule, mySettings, result);
    }
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
