// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.ui;

import com.intellij.facet.*;
import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.openapi.wm.impl.WindowInfoImpl;
import com.intellij.toolWindow.RegisterToolWindowTaskProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class FacetDependentToolWindowManager implements RegisterToolWindowTaskProvider {
  @Override
  public @NotNull Collection<ToolWindowEP> getTasks(@NotNull Project project) {
    List<FacetDependentToolWindow> facetDependentToolWindows = FacetDependentToolWindow.EXTENSION_POINT_NAME.getExtensionList();
    if (facetDependentToolWindows.isEmpty()) {
      return Collections.emptyList();
    }

    Collection<ToolWindowEP> result = new ArrayList<>();
    ProjectFacetManager projectFacetManager = ProjectFacetManager.getInstance(project);
    l: for (FacetDependentToolWindow extension : facetDependentToolWindows) {
      for (FacetType<?, ?> type : extension.getFacetTypes()) {
        if (projectFacetManager.hasFacets(type.getId())) {
          result.add(extension);
          continue l;
        }
      }
    }

    projectOpened(project);
    return result;
  }

  private static void projectOpened(@NotNull Project project) {
    ProjectWideFacetListenersRegistry.getInstance(project).registerListener(new ProjectWideFacetAdapter<>() {
      @Override
      public void facetAdded(@NotNull Facet facet) {
        checkIfToolwindowMustBeAdded(facet.getType());
      }

      @Override
      public void facetRemoved(@NotNull Facet facet) {
        checkIfToolwindowMustBeRemoved(facet.getType());
      }

      private void checkIfToolwindowMustBeAdded(FacetType<?, ?> facetType) {
        ToolWindowManager.getInstance(project).invokeLater(() -> {
          for (FacetDependentToolWindow extension : getDependentExtensions(facetType)) {
            ensureToolWindowExists(extension, project);
          }
        });
      }

      private void checkIfToolwindowMustBeRemoved(FacetType<?, ?> removedFacetType) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.invokeLater(() -> {
          ProjectFacetManager facetManager = ProjectFacetManager.getInstance(project);
          if (facetManager.hasFacets(removedFacetType.getId())) {
            return;
          }

          for (FacetDependentToolWindow extension : getDependentExtensions(removedFacetType)) {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(extension.id);
            if (toolWindow != null) {
              // check for other facets
              for (FacetType<?, ?> facetType : extension.getFacetTypes()) {
                if (facetManager.hasFacets(facetType.getId())) {
                  return;
                }
              }
              toolWindow.remove();
            }
          }
        });
      }
    }, project);

    FacetDependentToolWindow.EXTENSION_POINT_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull FacetDependentToolWindow extension, @NotNull PluginDescriptor pluginDescriptor) {
        initToolWindowIfNeeded(extension, project);
      }

      @Override
      public void extensionRemoved(@NotNull FacetDependentToolWindow extension, @NotNull PluginDescriptor pluginDescriptor) {
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(extension.id);
        if (window != null) {
          window.remove();
        }
      }
    }, project);
  }

  private static void initToolWindowIfNeeded(@NotNull FacetDependentToolWindow extension, @NotNull Project project) {
    ProjectFacetManager projectFacetManager = ProjectFacetManager.getInstance(project);
    for (FacetType<?, ?> type : extension.getFacetTypes()) {
      if (projectFacetManager.hasFacets(type.getId())) {
        ensureToolWindowExists(extension, project);
        return;
      }
    }
  }

  private static void ensureToolWindowExists(@NotNull FacetDependentToolWindow extension, @NotNull Project project) {
    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(extension.id);
    if (toolWindow == null) {
      ((ToolWindowManagerImpl)toolWindowManager).initToolWindow(extension);

      if (!extension.showOnStripeByDefault) {
        toolWindow = toolWindowManager.getToolWindow(extension.id);
        if (toolWindow != null) {
          WindowInfoImpl windowInfo = toolWindowManager.getLayout().getInfo(extension.id);
          if (windowInfo != null && !windowInfo.isFromPersistentSettings()) {
            toolWindow.setShowStripeButton(false);
          }
        }
      }
    }
  }

  private static @Unmodifiable @NotNull List<FacetDependentToolWindow> getDependentExtensions(@NotNull FacetType<?, ?> facetType) {
    return ContainerUtil.filter(FacetDependentToolWindow.EXTENSION_POINT_NAME.getExtensionList(),
                                toolWindowEP -> ArrayUtil.contains(facetType.getStringId(), toolWindowEP.getFacetIds()));
  }
}
