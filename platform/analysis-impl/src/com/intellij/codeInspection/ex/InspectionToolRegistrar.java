/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

/**
 * @author max
 */
public class InspectionToolRegistrar implements Supplier<List<InspectionToolWrapper>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolRegistrar");

  private final List<Supplier<InspectionToolWrapper>> myInspectionToolFactories = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myInspectionComponentsLoaded;

  private synchronized void ensureInitialized() {
    if (myInspectionComponentsLoaded) {
      return;
    }

    myInspectionComponentsLoaded = true;
    Set<InspectionToolProvider> providers = new THashSet<>();
    //noinspection deprecation
    providers.addAll((((ComponentManagerImpl)ApplicationManager.getApplication()).getComponentInstancesOfType(InspectionToolProvider.class)));
    ContainerUtil.addAll(providers, InspectionToolProvider.EXTENSION_POINT_NAME.getExtensions());
    List<Supplier<InspectionToolWrapper>> factories = new ArrayList<>();
    registerTools(providers, factories);
    boolean isInternal = ApplicationManager.getApplication().isInternal();
    Map<String, InspectionEP> shortNames = new THashMap<>();
    for (LocalInspectionEP ep : LocalInspectionEP.LOCAL_INSPECTION.getExtensions()) {
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
    myInspectionToolFactories.addAll(factories);
  }

  private static void checkForDuplicateShortName(InspectionEP ep, Map<String, InspectionEP> shortNames) {
    final String shortName = ep.getShortName();
    final InspectionEP duplicate = shortNames.put(shortName, ep);
    if (duplicate != null) {
      LOG.error("Short name '" + shortName + "' is not unique\n" +
                "class '" + ep.instantiateTool().getClass().getCanonicalName() + "' in " + ep.getPluginDescriptor() +
                "\nand\nclass'" + duplicate.instantiateTool().getClass().getCanonicalName() + "' in " + duplicate.getPluginDescriptor() + "\nconflict");
    }
  }

  @NotNull
  public static InspectionToolWrapper wrapTool(@NotNull InspectionProfileEntry profileEntry) {
    if (profileEntry instanceof LocalInspectionTool) {
      //noinspection TestOnlyProblems
      return new LocalInspectionToolWrapper((LocalInspectionTool)profileEntry);
    }
    if (profileEntry instanceof GlobalInspectionTool) {
      return new GlobalInspectionToolWrapper((GlobalInspectionTool)profileEntry);
    }
    throw new RuntimeException("unknown inspection class: " + profileEntry + "; "+profileEntry.getClass());
  }

  private static void registerTools(@NotNull Collection<InspectionToolProvider> providers,
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
    ensureInitialized();

    List<InspectionToolWrapper> tools = new ArrayList<>(myInspectionToolFactories.size());
    for (Supplier<InspectionToolWrapper> factory : myInspectionToolFactories) {
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
