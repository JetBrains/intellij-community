/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Factory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class InspectionToolRegistrar {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolRegistrar");

  private final List<Factory<InspectionToolWrapper>> myInspectionToolFactories = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myInspectionComponentsLoaded = false;

  private synchronized void ensureInitialized() {
    if (!myInspectionComponentsLoaded) {
      myInspectionComponentsLoaded = true;
      Set<InspectionToolProvider> providers = new THashSet<InspectionToolProvider>();
      //noinspection unchecked
      providers.addAll((((ComponentManagerEx)ApplicationManager.getApplication()).getComponentInstancesOfType(InspectionToolProvider.class)));
      ContainerUtil.addAll(providers, Extensions.getExtensions(InspectionToolProvider.EXTENSION_POINT_NAME));
      List<Factory<InspectionToolWrapper>> factories = new ArrayList<Factory<InspectionToolWrapper>>();
      registerTools(providers, factories);
      final boolean isInternal = ApplicationManager.getApplication().isInternal();
      for (final LocalInspectionEP ep : Extensions.getExtensions(LocalInspectionEP.LOCAL_INSPECTION)) {
        if (!isInternal && ep.isInternal) continue;
        factories.add(new Factory<InspectionToolWrapper>() {
          @Override
          public InspectionToolWrapper create() {
            return new LocalInspectionToolWrapper(ep);
          }
        });
      }
      for (final InspectionEP ep : Extensions.getExtensions(InspectionEP.GLOBAL_INSPECTION)) {
        if (!isInternal && ep.isInternal) continue;
        factories.add(new Factory<InspectionToolWrapper>() {
          @Override
          public InspectionToolWrapper create() {
            return new GlobalInspectionToolWrapper(ep);
          }
        });
      }
      for (InspectionToolsFactory factory : Extensions.getExtensions(InspectionToolsFactory.EXTENSION_POINT_NAME)) {
        for (final InspectionProfileEntry profileEntry : factory.createTools()) {
          factories.add(new Factory<InspectionToolWrapper>() {
            @Override
            public InspectionToolWrapper create() {
              return wrapTool(profileEntry);
            }
          });
        }
      }
      myInspectionToolFactories.addAll(factories);
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

  private static void registerTools(@NotNull Collection<InspectionToolProvider> providers,
                                    @NotNull List<Factory<InspectionToolWrapper>> factories) {
    for (InspectionToolProvider provider : providers) {
      Class[] classes = provider.getInspectionClasses();
      for (final Class aClass : classes) {
        Factory<InspectionToolWrapper> factory = new Factory<InspectionToolWrapper>() {
          @Override
          public InspectionToolWrapper create() {
            return wrapTool((InspectionProfileEntry)InspectionToolsRegistrarCore.instantiateTool(aClass));
          }
        };
        factories.add(factory);
      }
    }
  }

  public static InspectionToolRegistrar getInstance() {
    return ServiceManager.getService(InspectionToolRegistrar.class);
  }

  @NotNull
  public List<InspectionToolWrapper> createTools() {
    ensureInitialized();

    final List<InspectionToolWrapper> tools = new ArrayList<InspectionToolWrapper>(myInspectionToolFactories.size());
    for (final Factory<InspectionToolWrapper> factory : myInspectionToolFactories) {
      ProgressManager.checkCanceled();
      final InspectionToolWrapper toolWrapper = factory.create();
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
