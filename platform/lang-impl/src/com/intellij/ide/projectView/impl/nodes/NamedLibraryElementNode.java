/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableWithText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NamedLibraryElementNode extends ProjectViewNode<NamedLibraryElement> implements NavigatableWithText {
  private static final Icon GENERIC_JDK_ICON = IconLoader.getIcon("/general/jdk.png");
  private static final Icon LIB_ICON_OPEN = IconLoader.getIcon("/nodes/ppLibOpen.png");
  private static final Icon LIB_ICON_CLOSED = IconLoader.getIcon("/nodes/ppLibClosed.png");

  public NamedLibraryElementNode(Project project, NamedLibraryElement value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    LibraryGroupNode.addLibraryChildren(getValue().getOrderEntry(), children, getProject(), this);
    return children;
  }

  public String getTestPresentation() {
    return "Library: " + getValue().getName();
  }

  private static Icon getJdkIcon(JdkOrderEntry entry, boolean isExpanded) {
    final Sdk jdk = entry.getJdk();
    if (jdk == null) {
      return GENERIC_JDK_ICON;
    }
    return isExpanded? jdk.getSdkType().getIconForExpandedTreeNode() : jdk.getSdkType().getIcon();
  }

  public String getName() {
    return getValue().getName();
  }

  public boolean contains(@NotNull VirtualFile file) {
    return orderEntryContainsFile(getValue().getOrderEntry(), file);
  }

  private static boolean orderEntryContainsFile(OrderEntry orderEntry, VirtualFile file) {
    for(OrderRootType rootType: OrderRootType.getAllTypes()) {
      if (containsFileInOrderType(orderEntry, rootType, file)) return true;
    }
    return false;
  }

  private static boolean containsFileInOrderType(final OrderEntry orderEntry, final OrderRootType orderType, final VirtualFile file) {
    if (!orderEntry.isValid()) return false;
    VirtualFile[] files = orderEntry.getFiles(orderType);
    for (VirtualFile virtualFile : files) {
      boolean ancestor = VfsUtilCore.isAncestor(virtualFile, file, false);
      if (ancestor) return true;
    }
    return false;
  }

  public void update(PresentationData presentation) {
    presentation.setPresentableText(getValue().getName());
    final OrderEntry orderEntry = getValue().getOrderEntry();
    presentation.setOpenIcon(orderEntry instanceof JdkOrderEntry ? getJdkIcon((JdkOrderEntry)orderEntry, true) : LIB_ICON_OPEN);
    presentation.setClosedIcon(orderEntry instanceof JdkOrderEntry ? getJdkIcon((JdkOrderEntry)orderEntry, false) : LIB_ICON_CLOSED);
    if (orderEntry instanceof JdkOrderEntry) {
      final JdkOrderEntry jdkOrderEntry = (JdkOrderEntry)orderEntry;
      final Sdk projectJdk = jdkOrderEntry.getJdk();
      if (projectJdk != null) { //jdk not specified
        final String path = projectJdk.getHomePath();
        if (path != null) {
          presentation.setLocationString(FileUtil.toSystemDependentName(path));
        }
      }
      presentation.setTooltip(null);
    }
    else {
      presentation.setTooltip(StringUtil.capitalize(IdeBundle.message("node.projectview.library", ((LibraryOrderEntry)orderEntry).getLibraryLevel())));
    }
  }

  public void navigate(final boolean requestFocus) {
    ProjectSettingsService.getInstance(myProject).openLibraryOrSdkSettings(getValue().getOrderEntry());
  }

  public boolean canNavigate() {
    return ProjectSettingsService.getInstance(myProject).canOpenLibraryOrSdkSettings(getValue().getOrderEntry());
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    return "Open Library Settings";
  }
}
