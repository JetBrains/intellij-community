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
package com.intellij.facet.impl.ui;

import com.intellij.facet.*;
import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class FacetDependentToolWindowManager extends AbstractProjectComponent {

  private final ProjectWideFacetListenersRegistry myFacetListenersRegistry;
  private final ProjectFacetManager myFacetManager;
  private final ToolWindowManagerEx myToolWindowManager;

  protected FacetDependentToolWindowManager(Project project,
                                            ProjectWideFacetListenersRegistry facetListenersRegistry,
                                            ProjectFacetManager facetManager,
                                            ToolWindowManagerEx toolWindowManager) {
    super(project);
    myFacetListenersRegistry = facetListenersRegistry;
    myFacetManager = facetManager;
    myToolWindowManager = toolWindowManager;
  }

  @Override
  public void projectOpened() {
    myFacetListenersRegistry.registerListener(new ProjectWideFacetAdapter<Facet>() {
      @Override
      public void facetAdded(Facet facet) {
        for (FacetDependentToolWindow extension : getDependentExtensions(facet)) {
          ensureToolWindowExists(extension);
        }
      }

      @Override
      public void facetRemoved(Facet facet) {
        if (!myFacetManager.hasFacets(facet.getTypeId())) {
          for (FacetDependentToolWindow extension : getDependentExtensions(facet)) {
            ToolWindow toolWindow = myToolWindowManager.getToolWindow(extension.id);
            if (toolWindow != null) {
              // check for other facets
              List<FacetType> facetTypes = extension.getFacetTypes();
              for (FacetType facetType : facetTypes) {
                if (myFacetManager.hasFacets(facetType.getId())) return;
              }
              myToolWindowManager.unregisterToolWindow(extension.id);
            }
          }
        }
      }
    }, myProject);

    FacetDependentToolWindow[] extensions = Extensions.getExtensions(FacetDependentToolWindow.EXTENSION_POINT_NAME);
    loop: for (FacetDependentToolWindow extension : extensions) {
      for (FacetType type : extension.getFacetTypes()) {
        if (myFacetManager.hasFacets(type.getId())) {
          ensureToolWindowExists(extension);
          continue loop;
        }
      }
    }
  }

  private void ensureToolWindowExists(FacetDependentToolWindow extension) {
    ToolWindow toolWindow = myToolWindowManager.getToolWindow(extension.id);
    if (toolWindow == null) {
      myToolWindowManager.initToolWindow(extension);
    }
  }

  private static List<FacetDependentToolWindow> getDependentExtensions(final Facet facet) {
    FacetDependentToolWindow[] extensions = Extensions.getExtensions(FacetDependentToolWindow.EXTENSION_POINT_NAME);
    return ContainerUtil.filter(extensions, new Condition<FacetDependentToolWindow>() {
      @Override
      public boolean value(FacetDependentToolWindow toolWindowEP) {
        for (String id : toolWindowEP.getFacetIds()) {
          if (facet.getType().getStringId().equals(id)) return true;
        }
        return false;
      }
    });
  }
}
