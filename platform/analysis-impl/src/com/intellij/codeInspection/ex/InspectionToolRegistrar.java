// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author max
 */
public final class InspectionToolRegistrar extends InspectionToolsSupplier {
  private static final Logger LOG = Logger.getInstance(InspectionToolRegistrar.class);

  @NotNull
  private final NotNullLazyValue<Collection<? extends Supplier<InspectionToolWrapper>>> myInspectionToolFactories = NotNullLazyValue.createValue(() -> {
    Application application = ApplicationManager.getApplication();
    MultiMap<Object, Supplier<InspectionToolWrapper>> factories = MultiMap.create();
    Map<String, InspectionEP> shortNames = new THashMap<>();
    registerToolProviders(application, factories);
    registerInspections(factories, application, shortNames, LocalInspectionEP.LOCAL_INSPECTION);
    registerInspections(factories, application, shortNames, InspectionEP.GLOBAL_INSPECTION);
    return factories.values();
  });

  private void unregisterInspectionOrProvider(@NotNull Object inspectionOrProvider, @NotNull MultiMap<Object, Supplier<InspectionToolWrapper>> factories) {
    Collection<Supplier<InspectionToolWrapper>> removedTools = factories.remove(inspectionOrProvider);
    if (removedTools != null) {
      for (Supplier<InspectionToolWrapper> removedTool : removedTools) {
        fireToolRemoved(removedTool);
      }
    }
  }

  private <T extends InspectionEP> void registerInspections(@NotNull MultiMap<Object, Supplier<InspectionToolWrapper>> factories,
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

  private static <T extends InspectionEP> Supplier<InspectionToolWrapper> registerInspection(@NotNull T inspection,
                                                                                             @NotNull Map<String, InspectionEP> shortNames,
                                                                                             boolean isInternal,
                                                                                             @NotNull MultiMap<Object, Supplier<InspectionToolWrapper>> factories) {
    checkForDuplicateShortName(inspection, shortNames);
    if (!isInternal && inspection.isInternal) {
      return null;
    }
    Supplier<InspectionToolWrapper> inspectionFactory = () -> inspection instanceof LocalInspectionEP
                                                              ? new LocalInspectionToolWrapper((LocalInspectionEP)inspection)
                                                              : new GlobalInspectionToolWrapper(inspection);
    factories.putValue(inspection, inspectionFactory);
    return inspectionFactory;
  }

  private void registerToolProviders(@NotNull Application application,
                                     @NotNull MultiMap<Object, Supplier<InspectionToolWrapper>> factories) {
    for (InspectionToolProvider provider : application.getComponentInstancesOfType(InspectionToolProvider.class)) {
      registerToolProvider(provider, factories);
    }
    for (InspectionToolProvider provider : InspectionToolProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      registerToolProvider(provider, factories);
    }
    InspectionToolProvider.EXTENSION_POINT_NAME.getPoint(application).addExtensionPointListener(
      new ExtensionPointListener<InspectionToolProvider>() {
        @Override
        public void extensionAdded(@NotNull InspectionToolProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
          for (Supplier<InspectionToolWrapper> supplier : registerToolProvider(provider, factories)) {
            fireToolAdded(supplier);
          }
        }

        @Override
        public void extensionRemoved(@NotNull InspectionToolProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
          unregisterInspectionOrProvider(provider, factories);
        }
      }, false, application);
  }

  private static Collection<Supplier<InspectionToolWrapper>> registerToolProvider(@NotNull InspectionToolProvider provider,
                                                                                  @NotNull MultiMap<Object, Supplier<InspectionToolWrapper>> factories) {
    Collection<Supplier<InspectionToolWrapper>> suppliers = new SmartList<>();
    for (Class<? extends InspectionProfileEntry> aClass : provider.getInspectionClasses()) {
      suppliers.add(() -> ObjectUtils.doIfNotNull(InspectionToolsRegistrarCore.instantiateTool(aClass), e -> wrapTool(e)));
    }
    factories.putValues(provider, suppliers);
    return suppliers;
  }

  private static void checkForDuplicateShortName(InspectionEP ep, Map<String, InspectionEP> shortNames) {
    final String shortName = ep.getShortName();
    final InspectionEP duplicate = shortNames.put(shortName, ep);
    if (duplicate != null) {
      final PluginDescriptor descriptor = ep.getPluginDescriptor();
      LOG.error(new PluginException(
        "Short name '" + shortName + "' is not unique\nclass '" + ep.instantiateTool().getClass().getCanonicalName() + "' in " + descriptor +
        "\nand\nclass'" + duplicate.instantiateTool().getClass().getCanonicalName() + "' in " + duplicate.getPluginDescriptor() + "\nconflict",
        descriptor.getPluginId()));
    }
  }

  @NotNull
  public static InspectionToolWrapper wrapTool(@NotNull InspectionProfileEntry profileEntry) {
    if (profileEntry instanceof LocalInspectionTool) {
      return new LocalInspectionToolWrapper((LocalInspectionTool)profileEntry);
    }
    if (profileEntry instanceof GlobalInspectionTool) {
      return new GlobalInspectionToolWrapper((GlobalInspectionTool)profileEntry);
    }
    throw new RuntimeException("unknown inspection class: " + profileEntry + "; "+profileEntry.getClass());
  }

  public static InspectionToolRegistrar getInstance() {
    return ServiceManager.getService(InspectionToolRegistrar.class);
  }

  private void fireToolAdded(@Nullable Supplier<InspectionToolWrapper> supplier) {
    InspectionToolWrapper inspectionToolWrapper = supplier != null ? supplier.get() : null;
    if (inspectionToolWrapper != null) {
      for (Listener listener : myListeners) {
        listener.toolAdded(inspectionToolWrapper);
      }
    }
  }

  private void fireToolRemoved(@Nullable Supplier<InspectionToolWrapper> supplier) {
    InspectionToolWrapper inspectionToolWrapper = supplier != null ? supplier.get() : null;
    if (inspectionToolWrapper != null) {
      for (Listener listener : myListeners) {
        listener.toolRemoved(inspectionToolWrapper);
      }
    }
  }

  @Override
  @NotNull
  public List<InspectionToolWrapper> createTools() {
    Collection<? extends Supplier<InspectionToolWrapper>> inspectionToolFactories = myInspectionToolFactories.getValue();
    List<InspectionToolWrapper> tools = new ArrayList<>(inspectionToolFactories.size());
    for (Supplier<InspectionToolWrapper> factory : inspectionToolFactories) {
      ProgressManager.checkCanceled();
      InspectionToolWrapper toolWrapper = factory.get();
      if (toolWrapper != null && checkTool(toolWrapper) == null) {
        tools.add(toolWrapper);
      }
    }
    return tools;
  }

  /**
   * @deprecated use {@link #createTools()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @NotNull
  public List<InspectionToolWrapper> get() {
    return createTools();
  }

  private static String checkTool(@NotNull final InspectionToolWrapper toolWrapper) {
    if (!(toolWrapper instanceof LocalInspectionToolWrapper)) {
      return null;
    }
    String message = null;
    try {
      final String id = toolWrapper.getID();
      if (id == null || !LocalInspectionTool.isValidID(id)) {
        message = InspectionsBundle.message("inspection.disabled.wrong.id", toolWrapper.getShortName(), id, LocalInspectionTool.VALID_ID_PATTERN);
      }
    }
    catch (Throwable t) {
      message = InspectionsBundle.message("inspection.disabled.error", toolWrapper.getShortName(), t.getMessage());
    }
    if (message != null) {
      LOG.error(message);
    }
    return message;
  }
}
