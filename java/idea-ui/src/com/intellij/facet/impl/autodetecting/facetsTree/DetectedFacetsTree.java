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

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.DetectedFacetPresentation;
import com.intellij.facet.impl.autodetecting.FacetDetectorRegistryEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
*/
public class DetectedFacetsTree extends CheckboxTreeBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.autodetecting.facetsTree.DetectedFacetsTree");
  private static final Icon MODULE_ICON = IconLoader.getIcon("/nodes/ModuleClosed.png");

  public DetectedFacetsTree(final Collection<? extends CheckedTreeNode> roots) {
    super(new FacetsCheckboxTreeCellRenderer(), createRoot(roots), new CheckPolicy(true, true, true, false));
  }

  private static CheckedTreeNode createRoot(final Collection<? extends CheckedTreeNode> nodes) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    for (CheckedTreeNode node : nodes) {
      if (!(node instanceof FacetTypeNode) && !(node instanceof ModuleNode) && !(node instanceof FacetNode)) {
        LOG.error("incorrect node class: " + node.getClass().getName());

      }
      root.add(node);
    }
    return root;
  }

  private static class FacetsCheckboxTreeCellRenderer extends CheckboxTreeCellRendererBase {
    public void customizeCellRenderer(final JTree tree,
                                        final Object value,
                                        final boolean selected,
                                        final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
      ColoredTreeCellRenderer renderer = getTextRenderer();
      if (value instanceof ModuleNode) {
        ModuleNode moduleNode = (ModuleNode)value;
        renderer.setIcon(MODULE_ICON);
        renderer.append(moduleNode.getModuleName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        renderer.append(moduleNode.getModuleDescription(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      else if (value instanceof FacetNode) {
        final FacetNode node = (FacetNode)value;
        FacetType type = node.getFacetType();
        renderer.setIcon(type.getIcon());
        renderer.append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        String description = node.getDescription();
        if (description != null) {
          renderer.append(" (" + description + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
      else if (value instanceof FacetTypeNode) {
        FacetType type = ((FacetTypeNode)value).getFacetType();
        renderer.setIcon(type.getIcon());
        renderer.append(ProjectBundle.message("detected.facet.type.node", type.getPresentableName()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }

  }

  public static abstract class ModuleNode extends CheckedTreeNode{
    private final List<FacetNode> myRootFacets = new ArrayList<FacetNode>();

    public ModuleNode(Object userObject) {
      super(userObject);
    }


    public abstract String getModuleName();

    public abstract String getModuleDescription();

    public void addRootFacet(FacetNode facetInfo) {
      myRootFacets.add(facetInfo);
      add(facetInfo);
    }

    public List<FacetNode> getRootFacets() {
      return myRootFacets;
    }
  }

  public static class FacetTypeNode extends CheckedTreeNode {
    private final FacetType myFacetType;
    private final List<ModuleNode> myModuleNodes = new ArrayList<ModuleNode>();

    public FacetTypeNode(final FacetType facetType) {
      super(facetType);
      myFacetType = facetType;
    }

    public FacetType getFacetType() {
      return myFacetType;
    }

    public void addModuleNode(ModuleNode moduleNode) {
      myModuleNodes.add(moduleNode);
      add(moduleNode);
    }

    public List<ModuleNode> getModuleNodes() {
      return myModuleNodes;
    }
  }

  public static class FacetNode extends CheckedTreeNode {
    private final VirtualFile[] myFiles;
    private final FacetType<?,?> myFacetType;
    private final VirtualFile myProjectRoot;
    private final List<FacetNode> myChildren = new ArrayList<FacetNode>();

    public FacetNode(Object userObject, FacetType facetType, VirtualFile projectRoot, final VirtualFile[] files, @Nullable FacetNode parent) {
      super(userObject);
      myFacetType = facetType;
      myProjectRoot = projectRoot;
      myFiles = files;
      if (parent != null) {
        parent.myChildren.add(this);
        parent.add(this);
      }
    }

    public String getName() {
      return myFacetType.getPresentableName();
    }

    public VirtualFile[] getFiles() {
      return myFiles;
    }

    public List<FacetNode> getChildren() {
      return myChildren;
    }

    public FacetType getFacetType() {
      return myFacetType;
    }

    @Nullable
    public String getDescription() {
      DetectedFacetPresentation presentation = FacetDetectorRegistryEx.getDetectedFacetPresentation(myFacetType);
      return presentation.getDetectedFacetDescription(myProjectRoot, myFiles);
    }
  }
}
