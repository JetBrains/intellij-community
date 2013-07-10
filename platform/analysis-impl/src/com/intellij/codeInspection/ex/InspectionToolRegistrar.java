/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Factory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class InspectionToolRegistrar {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolRegistrar");

  private final List<Factory<InspectionToolWrapper>> myInspectionToolFactories = new ArrayList<Factory<InspectionToolWrapper>>();

  private final AtomicBoolean myInspectionComponentsLoaded = new AtomicBoolean(false);

  public void ensureInitialized() {
    if (!myInspectionComponentsLoaded.getAndSet(true)) {
      Set<InspectionToolProvider> providers = new THashSet<InspectionToolProvider>();
      ContainerUtil.addAll(providers, ApplicationManager.getApplication().getComponents(InspectionToolProvider.class));
      ContainerUtil.addAll(providers, Extensions.getExtensions(InspectionToolProvider.EXTENSION_POINT_NAME));
      registerTools(providers.toArray(new InspectionToolProvider[providers.size()]));
      for (final LocalInspectionEP ep : Extensions.getExtensions(LocalInspectionEP.LOCAL_INSPECTION)) {
        myInspectionToolFactories.add(new Factory<InspectionToolWrapper>() {
          @Override
          public InspectionToolWrapper create() {
            return new LocalInspectionToolWrapper(ep);
          }
        });
      }
      for (final InspectionEP ep : Extensions.getExtensions(InspectionEP.GLOBAL_INSPECTION)) {
        myInspectionToolFactories.add(new Factory<InspectionToolWrapper>() {
          @Override
          public InspectionToolWrapper create() {
            return new GlobalInspectionToolWrapper(ep);
          }
        });
      }
      for (InspectionToolsFactory factory : Extensions.getExtensions(InspectionToolsFactory.EXTENSION_POINT_NAME)) {
        for (final InspectionProfileEntry profileEntry : factory.createTools()) {
          myInspectionToolFactories.add(new Factory<InspectionToolWrapper>() {
            @Override
            public InspectionToolWrapper create() {
              return wrapTool(profileEntry);
            }
          });
        }
      }
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

  public void registerTools(@NotNull InspectionToolProvider[] providers) {
    for (InspectionToolProvider provider : providers) {
      Class[] classes = provider.getInspectionClasses();
      for (Class aClass : classes) {
        registerInspectionTool(aClass);
      }
    }
  }

  @NotNull
  private Factory<InspectionToolWrapper> registerInspectionTool(@NotNull final Class aClass) {
    if (LocalInspectionTool.class.isAssignableFrom(aClass)) {
      return registerLocalInspection(aClass, true);
    }
    if (GlobalInspectionTool.class.isAssignableFrom(aClass)) {
      return registerGlobalInspection(aClass, true);
    }
    throw new RuntimeException("unknown inspection class: " + aClass);
  }

  public static InspectionToolRegistrar getInstance() {
    return ServiceManager.getService(InspectionToolRegistrar.class);
  }

  /**
   * make sure that it is not too late
   */
  @NotNull
  public Factory<InspectionToolWrapper> registerInspectionToolFactory(@NotNull Factory<InspectionToolWrapper> factory, boolean store) {
    if (store) {
      myInspectionToolFactories.add(factory);
    }
    return factory;
  }

  @NotNull
  private Factory<InspectionToolWrapper> registerLocalInspection(final Class toolClass, boolean store) {
    return registerInspectionToolFactory(new Factory<InspectionToolWrapper>() {
      @Override
      public InspectionToolWrapper create() {
        return new LocalInspectionToolWrapper((LocalInspectionTool)InspectionToolsRegistrarCore.instantiateTool(toolClass));
      }
    }, store);
  }

  @NotNull
  private Factory<InspectionToolWrapper> registerGlobalInspection(@NotNull final Class aClass, boolean store) {
    return registerInspectionToolFactory(new Factory<InspectionToolWrapper>() {
      @Override
      public InspectionToolWrapper create() {
        return new GlobalInspectionToolWrapper((GlobalInspectionTool) InspectionToolsRegistrarCore.instantiateTool(aClass));
      }
    }, store);
  }

  @NotNull
  @TestOnly
  public List<InspectionToolWrapper> createTools() {
    ensureInitialized();

    final List<InspectionToolWrapper> tools = ContainerUtil.newArrayListWithCapacity(myInspectionToolFactories.size());
    final Set<Factory<InspectionToolWrapper>> broken = ContainerUtil.newHashSet();
    for (final Factory<InspectionToolWrapper> factory : myInspectionToolFactories) {
      ProgressManager.checkCanceled();
      final InspectionToolWrapper toolWrapper = factory.create();
      if (toolWrapper != null && checkTool(toolWrapper) == null) {
        tools.add(toolWrapper);
      }
      else {
        broken.add(factory);
      }
    }
    myInspectionToolFactories.removeAll(broken);

    return tools;
  }

  private static String checkTool(@NotNull final InspectionToolWrapper toolWrapper) {
    if (!(toolWrapper instanceof LocalInspectionToolWrapper)) {
      return null;
    }
    String message = null;
    try {
      final String id = ((LocalInspectionToolWrapper)toolWrapper).getID();
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
