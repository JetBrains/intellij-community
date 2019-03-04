// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

/**
 * @author max
 */
public class InspectionToolRegistrar implements Supplier<List<InspectionToolWrapper>> {
  private static final Logger LOG = Logger.getInstance(InspectionToolRegistrar.class);

  @NotNull
  private final NotNullLazyValue<List<Supplier<InspectionToolWrapper>>> myInspectionToolFactories = NotNullLazyValue.createValue(() -> {
    Set<InspectionToolProvider> providers = new THashSet<>();
    providers.addAll((((ComponentManagerImpl)ApplicationManager.getApplication()).getComponentInstancesOfType(InspectionToolProvider.class)));
    providers.addAll(InspectionToolProvider.EXTENSION_POINT_NAME.getExtensionList());

    List<Supplier<InspectionToolWrapper>> factories = new ArrayList<>();
    registerTools(providers, factories);
    boolean isInternal = ApplicationManager.getApplication().isInternal();
    Map<String, InspectionEP> shortNames = new THashMap<>();
    for (LocalInspectionEP ep : LocalInspectionEP.LOCAL_INSPECTION.getExtensionList()) {
      checkForDuplicateShortName(ep, shortNames);
      if (!isInternal && ep.isInternal) {
        continue;
      }

      factories.add(() -> new LocalInspectionToolWrapper(ep));
    }

    for (InspectionEP ep : InspectionEP.GLOBAL_INSPECTION.getExtensions()) {
      checkForDuplicateShortName(ep, shortNames);
      if (!isInternal && ep.isInternal) {
        continue;
      }

      factories.add(() -> new GlobalInspectionToolWrapper(ep));
    }
    return factories;
  });

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

  private static void registerTools(@NotNull Collection<? extends InspectionToolProvider> providers,
                                    @NotNull List<Supplier<InspectionToolWrapper>> factories) {
    for (InspectionToolProvider provider : providers) {
      //noinspection unchecked
      for (Class<InspectionProfileEntry> aClass : provider.getInspectionClasses()) {
        factories.add(() -> {
          InspectionProfileEntry entry = InspectionToolsRegistrarCore.instantiateTool(aClass);
          return entry == null ? null : wrapTool(entry);
        });
      }
    }
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
    List<Supplier<InspectionToolWrapper>> inspectionToolFactories = myInspectionToolFactories.getValue();
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
