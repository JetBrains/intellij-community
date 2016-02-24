/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui.tree;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionResultsViewComparator;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author Dmitry Batkovich
 */
public abstract class InspectionTreeNode<T> extends AbstractTreeNode<T> {
  protected static final Logger LOG = Logger.getInstance(InspectionTreeNode.class);

  private boolean myResolved; //accessed from EDT
  private Set<InspectionTreeNode> myChildren = new ConcurrentSkipListSet<>(InspectionResultsViewComparator.getInstance());

  protected InspectionTreeNode(Project project, T value) {
    super(project, value);
  }

  public boolean isValid() {
    return true;
  }

  public boolean isResolved() {
    return myResolved;
  }

  public boolean appearsBold() {
    return false;
  }

  public FileStatus getNodeStatus() {
    return FileStatus.NOT_CHANGED;
  }

  public void ignoreElement() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myResolved = true;
    for (InspectionTreeNode node : getChildren()) {
      node.ignoreElement();
    }
  }

  public void amnesty() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myResolved = false;
    for (InspectionTreeNode node : getChildren()) {
      node.amnesty();
    }
  }

  public int getProblemCount() {
    int sum = 0;
    for (InspectionTreeNode node : getChildren()) {
      sum += node.getProblemCount();
    }
    return sum;
  }

  public final void add(InspectionTreeNode node) {
    myChildren.add(node);
    node.setParent(this);
  }

  @NotNull
  @Override
  public final Collection<InspectionTreeNode> getChildren() {
    return myChildren;
  }

  public final void removeAllChildren() {
    myChildren = new ConcurrentSkipListSet<>(InspectionResultsViewComparator.getInstance());
  }

  @Nullable
  public Icon getIcon(boolean expanded) {
    return null;
  }

  @Override
  protected final void update(PresentationData presentation) {
    presentation.setIcon(getIcon(false));
    presentation.addText(toString(),
                         patchAttr(this, appearsBold() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : getMainForegroundAttributes(this)));

    int problemCount = getProblemCount();
    if (!getChildren().isEmpty()) {
      presentation.addText(" " + InspectionsBundle.message("inspection.problem.descriptor.count", problemCount),
                           patchAttr(this, SimpleTextAttributes.GRAYED_ATTRIBUTES));
    }

    if (!isValid()) {
      presentation
        .addText(" " + InspectionsBundle.message("inspection.invalid.node.text"), patchAttr(this, SimpleTextAttributes.ERROR_ATTRIBUTES));
    }
    else {
      setIcon(getIcon(false));
    }
  }

  //TODO Dmitry Batkovich
  public static SimpleTextAttributes patchAttr(InspectionTreeNode node, SimpleTextAttributes attributes) {
    if (node.isResolved()) {
      return new SimpleTextAttributes(attributes.getBgColor(), attributes.getFgColor(), attributes.getWaveColor(),
                                      attributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT);
    }
    return attributes;
  }

  private static SimpleTextAttributes getMainForegroundAttributes(InspectionTreeNode node) {
    SimpleTextAttributes foreground = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (node instanceof RefElementNode) {
      RefEntity refElement = ((RefElementNode)node).getRefElement();

      if (refElement instanceof RefElement) {
        refElement = ((RefElement)refElement).getContainingEntry();
        if (((RefElement)refElement).isEntry() && ((RefElement)refElement).isPermanentEntry()) {
          foreground = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.blue);
        }
      }
    }
    final FileStatus nodeStatus = node.getNodeStatus();
    if (nodeStatus != FileStatus.NOT_CHANGED) {
      foreground =
        new SimpleTextAttributes(foreground.getBgColor(), nodeStatus.getColor(), foreground.getWaveColor(), foreground.getStyle());
    }
    return foreground;
  }
}
