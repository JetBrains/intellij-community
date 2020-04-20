// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.*;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Supplier;

@Service
public final class InspectionToolRegistrar extends InspectionToolsSupplier {
  private static final Logger LOG = Logger.getInstance(InspectionToolRegistrar.class);
  private static final ExtensionPointName<InspectionToolProvider> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.inspectionToolProvider");

  private final @NotNull NotNullLazyValue<Collection<List<Supplier<InspectionToolWrapper<?, ?>>>>> toolFactories = NotNullLazyValue.createValue(() -> {
    Application application = ApplicationManager.getApplication();
    Map<Object, List<Supplier<InspectionToolWrapper<?, ?>>>> factories = new HashMap<>();
    Map<String, InspectionEP> shortNames = new THashMap<>();
    registerToolProviders(application, factories);
    registerInspections(factories, application, shortNames, LocalInspectionEP.LOCAL_INSPECTION);
    registerInspections(factories, application, shortNames, InspectionEP.GLOBAL_INSPECTION);
    return factories.values();
  });

  public static InspectionToolRegistrar getInstance() {
    return ApplicationManager.getApplication().getService(InspectionToolRegistrar.class);
  }

  private void unregisterInspectionOrProvider(@NotNull Object inspectionOrProvider, @NotNull Map<Object, List<Supplier<InspectionToolWrapper<?, ?>>>> factories) {
    List<Supplier<InspectionToolWrapper<?, ?>>> removedTools = factories.remove(inspectionOrProvider);
    if (removedTools != null) {
      for (Supplier<InspectionToolWrapper<?, ?>> removedTool : removedTools) {
        fireToolRemoved(removedTool);
      }
    }
  }

  private <T extends InspectionEP> void registerInspections(@NotNull Map<Object, List<Supplier<InspectionToolWrapper<?, ?>>>> factories,
                                                            @NotNull Application application,
                                                            @NotNull Map<String, InspectionEP> shortNames,
                                                            @NotNull ExtensionPointName<T> extensionPointName) {
    boolean isInternal = application.isInternal();
    for (T extension : extensionPointName.getExtensionList()) {
      registerInspection(extension, shortNames, isInternal, factories);
    }
    extensionPointName.getPoint(application).addExtensionPointListener(new ExtensionPointListener<T>() {
      @Override
      public void extensionAdded(@NotNull T inspection, @NotNull PluginDescriptor pluginDescriptor) {
        fireToolAdded(registerInspection(inspection, shortNames, isInternal, factories));
      }

      @Override
      public void extensionRemoved(@NotNull T inspection, @NotNull PluginDescriptor pluginDescriptor) {
        unregisterInspectionOrProvider(inspection, factories);
        shortNames.remove(inspection.getShortName());
      }
    }, false, application);
  }

  private static @Nullable <T extends InspectionEP> Supplier<InspectionToolWrapper<?, ?>> registerInspection(@NotNull T inspection,
                                                                                                             @NotNull Map<String, InspectionEP> shortNames,
                                                                                                             boolean isInternal,
                                                                                                             @NotNull Map<Object, List<Supplier<InspectionToolWrapper<?, ?>>>> factories) {
    checkForDuplicateShortName(inspection, shortNames);
    if (!isInternal && inspection.isInternal) {
      return null;
    }

    Supplier<InspectionToolWrapper<?, ?>> inspectionFactory = () -> {
      return inspection instanceof LocalInspectionEP
             ? new LocalInspectionToolWrapper((LocalInspectionEP)inspection)
             : new GlobalInspectionToolWrapper(inspection);
    };
    factories.computeIfAbsent(inspection, o -> new ArrayList<>()).add(inspectionFactory);
    return inspectionFactory;
  }

  private void registerToolProviders(@NotNull Application app, @NotNull Map<Object, List<Supplier<InspectionToolWrapper<?, ?>>>> factories) {
    if (app.isUnitTestMode()) {
      //noinspection deprecation
      LOG.assertTrue(app.getComponentInstancesOfType(InspectionToolProvider.class).isEmpty());
    }

    EXTENSION_POINT_NAME.processWithPluginDescriptor((provider, pluginDescriptor) -> {
      registerToolProvider(provider, pluginDescriptor, factories, null);
    });

    EXTENSION_POINT_NAME.getPoint(app).addExtensionPointListener(
      new ExtensionPointListener<InspectionToolProvider>() {
        @Override
        public void extensionAdded(@NotNull InspectionToolProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
          List<Supplier<InspectionToolWrapper<?, ?>>> added = new ArrayList<>();
          registerToolProvider(provider, pluginDescriptor, factories, added);
          for (Supplier<InspectionToolWrapper<?, ?>> supplier : added) {
            fireToolAdded(supplier);
          }
        }

        @Override
        public void extensionRemoved(@NotNull InspectionToolProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
          unregisterInspectionOrProvider(provider, factories);
        }
      }, false, app);
  }

  private static void registerToolProvider(@NotNull InspectionToolProvider provider,
                                           @NotNull PluginDescriptor pluginDescriptor,
                                           @NotNull Map<Object, List<Supplier<InspectionToolWrapper<?, ?>>>> factories,
                                           @Nullable List<Supplier<InspectionToolWrapper<?, ?>>> newlyAddedToCollect) {
    List<Supplier<InspectionToolWrapper<?, ?>>> suppliers = factories.computeIfAbsent(provider, k -> new ArrayList<>());
    for (Class<? extends InspectionProfileEntry> aClass : provider.getInspectionClasses()) {
      Supplier<InspectionToolWrapper<?, ?>> supplier = createSupplier(pluginDescriptor, aClass);
      suppliers.add(supplier);
      if (newlyAddedToCollect != null) {
        newlyAddedToCollect.add(supplier);
      }
    }
  }

  @NotNull
  private static Supplier<InspectionToolWrapper<?, ?>> createSupplier(@NotNull PluginDescriptor pluginDescriptor,
                                                                      @NotNull Class<? extends InspectionProfileEntry> aClass) {
    return () -> {
      try {
        Constructor<? extends InspectionProfileEntry> constructor = aClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return wrapTool(constructor.newInstance());
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(new PluginException(e, pluginDescriptor.getPluginId()));
        return null;
      }
    };
  }

  private static void checkForDuplicateShortName(InspectionEP ep, Map<String, InspectionEP> shortNames) {
    final String shortName = ep.getShortName();
    final InspectionEP duplicate = shortNames.put(shortName, ep);
    if (duplicate != null) {
      final PluginDescriptor descriptor = ep.getPluginDescriptor();
      LOG.error(new PluginException(
        "Short name '" + shortName + "' is not unique\nclass '" + ep.instantiateTool().getClass().getCanonicalName() + "' in " + descriptor +
        "\nand\nclass '" + duplicate.instantiateTool().getClass().getCanonicalName() + "' in " + duplicate.getPluginDescriptor() + "\nconflict",
        descriptor.getPluginId()));
    }
  }

  @ApiStatus.Internal
  public static @NotNull InspectionToolWrapper<?, ?> wrapTool(@NotNull InspectionProfileEntry profileEntry) {
    if (profileEntry instanceof LocalInspectionTool) {
      return new LocalInspectionToolWrapper((LocalInspectionTool)profileEntry);
    }
    else if (profileEntry instanceof GlobalInspectionTool) {
      return new GlobalInspectionToolWrapper((GlobalInspectionTool)profileEntry);
    }
    else {
      throw new RuntimeException("unknown inspection class: " + profileEntry + "; "+profileEntry.getClass());
    }
  }

  private void fireToolAdded(@Nullable Supplier<InspectionToolWrapper<?, ?>> supplier) {
    InspectionToolWrapper<?, ?> inspectionToolWrapper = supplier != null ? supplier.get() : null;
    if (inspectionToolWrapper != null) {
      for (Listener listener : myListeners) {
        listener.toolAdded(inspectionToolWrapper);
      }
    }
  }

  private void fireToolRemoved(@Nullable Supplier<InspectionToolWrapper<?, ?>> supplier) {
    InspectionToolWrapper<?, ?> inspectionToolWrapper = supplier != null ? supplier.get() : null;
    if (inspectionToolWrapper != null) {
      for (Listener listener : myListeners) {
        listener.toolRemoved(inspectionToolWrapper);
      }
    }
  }

  @Override
  public @NotNull List<InspectionToolWrapper<?, ?>> createTools() {
    Collection<List<Supplier<InspectionToolWrapper<?, ?>>>> inspectionToolFactories = toolFactories.getValue();
    ArrayList<InspectionToolWrapper<?, ?>> tools = new ArrayList<>();
    for (List<Supplier<InspectionToolWrapper<?, ?>>> list : inspectionToolFactories) {
      tools.ensureCapacity(list.size());
      for (Supplier<InspectionToolWrapper<?, ?>> factory : list) {
        ProgressManager.checkCanceled();
        InspectionToolWrapper<?, ?> toolWrapper = factory.get();
        if (toolWrapper != null && checkTool(toolWrapper) == null) {
          tools.add(toolWrapper);
        }
      }
    }
    return tools;
  }

  /**
   * @deprecated use {@link #createTools()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  public @NotNull List<InspectionToolWrapper<?, ?>> get() {
    return createTools();
  }

  private static String checkTool(@NotNull InspectionToolWrapper<?, ?> toolWrapper) {
    if (!(toolWrapper instanceof LocalInspectionToolWrapper)) {
      return null;
    }

    String message = null;
    try {
      String id = toolWrapper.getID();
      if (id == null || !LocalInspectionTool.isValidID(id)) {
        message = AnalysisBundle.message("inspection.disabled.wrong.id", toolWrapper.getShortName(), id, LocalInspectionTool.VALID_ID_PATTERN);
      }
    }
    catch (Throwable t) {
      message = AnalysisBundle.message("inspection.disabled.error", toolWrapper.getShortName(), t.getMessage());
    }
    if (message != null) {
      LOG.error(message);
    }
    return message;
  }
}
