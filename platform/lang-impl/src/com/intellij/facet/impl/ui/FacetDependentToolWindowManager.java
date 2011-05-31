package com.intellij.facet.impl.ui;

import com.intellij.facet.*;
import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class FacetDependentToolWindowManager extends AbstractProjectComponent {

  private final ProjectWideFacetListenersRegistry myFacetListenersRegistry;
  private final ProjectFacetManager myFacetManager;
  private final ToolWindowManagerImpl myToolWindowManager;

  protected FacetDependentToolWindowManager(Project project,
                                            ProjectWideFacetListenersRegistry facetListenersRegistry,
                                            ProjectFacetManager facetManager,
                                            ToolWindowManagerImpl toolWindowManager) {
    super(project);
    myFacetListenersRegistry = facetListenersRegistry;
    myFacetManager = facetManager;
    myToolWindowManager = toolWindowManager;
  }

  public void initComponent() {
    myFacetListenersRegistry.registerListener(new ProjectWideFacetAdapter<Facet>() {
      @Override
      public void facetAdded(Facet facet) {
        if (myFacetManager.getFacets(facet.getTypeId()).size() == 1) {
          for (ToolWindowEP extension : getDependentExtensions(facet)) {
            myToolWindowManager.initToolWindow(extension);
          }
        }
      }

      @Override
      public void facetRemoved(Facet facet) {
        if (myFacetManager.getFacets(facet.getTypeId()).isEmpty()) {
          for (ToolWindowEP extension : getDependentExtensions(facet)) {
            ToolWindow toolWindow = myToolWindowManager.getToolWindow(extension.id);
            if (toolWindow != null) {
              myToolWindowManager.unregisterToolWindow(extension.id);
            }
          }
        }
      }
    });
  }

  private static List<FacetDependentToolWindow> getDependentExtensions(final Facet facet) {
    FacetDependentToolWindow[] extensions = Extensions.getExtensions(FacetDependentToolWindow.EXTENSION_POINT_NAME);
    return ContainerUtil.filter(extensions, new Condition<FacetDependentToolWindow>() {
      @Override
      public boolean value(FacetDependentToolWindow toolWindowEP) {
        return facet.getType().getStringId().equals(toolWindowEP.facetId);
      }
    });
  }
}
