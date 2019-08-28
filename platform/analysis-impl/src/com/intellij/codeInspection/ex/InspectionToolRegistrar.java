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
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author max
 */
public final class InspectionToolRegistrar implements Supplier<List<InspectionToolWrapper>> {
  private static final Logger LOG = Logger.getInstance(InspectionToolRegistrar.class);

  @NotNull
  private final NotNullLazyValue<Collection<? extends Supplier<InspectionToolWrapper>>> myInspectionToolFactories = NotNullLazyValue.createValue(() -> {
    Application application = ApplicationManager.getApplication();
    MultiMap<Class<?>, Supplier<InspectionToolWrapper>> factories = MultiMap.create();
    Map<String, InspectionEP> shortNames = new THashMap<>();
    registerToolProviders(application, factories);
    registerInspections(factories, application, shortNames, LocalInspectionEP.LOCAL_INSPECTION);
    registerInspections(factories, application, shortNames, InspectionEP.GLOBAL_INSPECTION);
    return factories.values();
  });

  private static <T extends InspectionEP> void registerInspections(@NotNull MultiMap<Class<?>, Supplier<InspectionToolWrapper>> factories,
                                                                   @NotNull Application application,
                                                                   @NotNull Map<String, InspectionEP> shortNames,
                                                                   ExtensionPointName<T> extensionPointName) {
    boolean isInternal = application.isInternal();
    extensionPointName.getPoint(application).addExtensionPointListener(new ExtensionPointListener<T>() {
      @Override
      public void extensionAdded(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkForDuplicateShortName(extension, shortNames);
        if (!isInternal && extension.isInternal) {
          return;
        }
        factories.putValue(extension.getClass(), () -> extension instanceof LocalInspectionEP
                                                       ? new LocalInspectionToolWrapper((LocalInspectionEP)extension)
                                                       : new GlobalInspectionToolWrapper(extension));
      }

      @Override
      public void extensionRemoved(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
        shortNames.remove(extension.getShortName());
        factories.remove(extension.getClass());
      }
    }, true, application);
  }

  private static void registerToolProviders(@NotNull Application application,
                                            @NotNull MultiMap<Class<?>, Supplier<InspectionToolWrapper>> factories) {
    for (InspectionToolProvider provider : ((ComponentManagerImpl)application).getComponentInstancesOfType(InspectionToolProvider.class)) {
      registerToolProvider(provider, factories);
    }
    InspectionToolProvider.EXTENSION_POINT_NAME.getPoint(application).addExtensionPointListener(
      new ExtensionPointListener<InspectionToolProvider>() {
        @Override
        public void extensionAdded(@NotNull InspectionToolProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
          registerToolProvider(extension, factories);
        }

        @Override
        public void extensionRemoved(@NotNull InspectionToolProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
          factories.remove(extension.getClass());
        }
      }, true, application);
  }

  public static void registerToolProvider(@NotNull InspectionToolProvider extension,
                                          @NotNull MultiMap<Class<?>, Supplier<InspectionToolWrapper>> factories) {
    Class<? extends InspectionToolProvider> providerClass = extension.getClass();
    for (Class<? extends LocalInspectionTool> aClass : extension.getInspectionClasses()) {
      factories.putValue(providerClass, () ->
        ObjectUtils.doIfNotNull(InspectionToolsRegistrarCore.instantiateTool(aClass), e -> wrapTool(e)));
    }
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

  @Override
  @NotNull
  public List<InspectionToolWrapper> get() {
    return createTools();
  }

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
