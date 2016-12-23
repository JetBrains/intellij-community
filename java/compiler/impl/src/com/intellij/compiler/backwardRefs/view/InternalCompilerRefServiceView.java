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
package com.intellij.compiler.backwardRefs.view;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;

public class InternalCompilerRefServiceView extends JPanel implements DataProvider {
  private static final String TOOL_WINDOW_ID = "Compiler Reference View";
  private final Tree myTree;
  private final Project myProject;

  public InternalCompilerRefServiceView(Project project) {
    myProject = project;
    myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myTree.setRootVisible(false);
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        final Object userObject = node.getUserObject();
        if (userObject instanceof String) {
          append((String)userObject, SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        else if (userObject instanceof VirtualFile) {
          append(((VirtualFile)userObject).getName());
          append(" in ");
          append(((VirtualFile)userObject).getParent().getPath(), SimpleTextAttributes.GRAY_ATTRIBUTES);
        } else {
          append(userObject.toString());
        }
      }
    });
    setLayout(new BorderLayout());
    add(ToolbarDecorator.createDecorator(myTree).createPanel());
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      final TreePath path = myTree.getSelectionPath();
      if (path != null) {
        final Object usrObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
        if (usrObject instanceof VirtualFile) {
          return new OpenFileDescriptor(myProject, (VirtualFile)usrObject);
        }
      }
    }
    return null;
  }

  public static void showFindUsages(CompilerReferenceFindUsagesTestInfo info, PsiElement element) {
    final InternalCompilerRefServiceView view = getView(element);
    final DefaultMutableTreeNode node = info.asTree();
    node.setUserObject(element);
    ((DefaultTreeModel)view.myTree.getModel()).setRoot(node);
  }

  public static InternalCompilerRefServiceView getView(PsiElement element) {
    Project project = element.getProject();
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
    if (toolWindow == null) {
      toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID,
                                                        true,
                                                        ToolWindowAnchor.TOP);
    }
    final InternalCompilerRefServiceView view = new InternalCompilerRefServiceView(project);
    ToolWindow finalToolWindow = toolWindow;
    toolWindow.activate(() -> {
      final String text = SymbolPresentationUtil.getSymbolPresentableText(element);
      final ContentImpl content = new ContentImpl(view, text, true);
      finalToolWindow.getContentManager().addContent(content);
      finalToolWindow.getContentManager().setSelectedContent(content, true);
    });
    return view;
  }

}
