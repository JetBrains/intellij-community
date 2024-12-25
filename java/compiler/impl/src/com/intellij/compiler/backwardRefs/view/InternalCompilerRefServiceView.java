// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs.view;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;

public class InternalCompilerRefServiceView extends JPanel implements UiDataProvider {
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
        else if (userObject instanceof VirtualFile virtualFile) {
          append(virtualFile.getName() + " ");
          append(JavaCompilerBundle.message("label.in.path.suffix", virtualFile.getParent().getPath()),
                 SimpleTextAttributes.GRAY_ATTRIBUTES);
        } else if (userObject instanceof PsiFunctionalExpression) {
          append(ClassPresentationUtil.getFunctionalExpressionPresentation((PsiFunctionalExpression)userObject, true));
        } else if (userObject instanceof PsiClass) {
          append(ClassPresentationUtil.getNameForClass((PsiClass)userObject, true));
        } else {
          final @NlsSafe String text = userObject.toString();
          append(text);
        }
      }
    });
    setLayout(new BorderLayout());
    add(new JBScrollPane(myTree));
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    TreePath path = myTree.getSelectionPath();
    Object usrObject = TreeUtil.getLastUserObject(path);
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      return usrObject instanceof VirtualFile o ? new OpenFileDescriptor(myProject, o) :
             usrObject instanceof NavigatablePsiElement o ? o : null;
    });
  }

  public static void showFindUsages(CompilerReferenceFindUsagesTestInfo info, PsiElement element) {
    final InternalCompilerRefServiceView view = createViewTab(element);
    final DefaultMutableTreeNode node = info.asTree();
    node.setUserObject(element);
    ((DefaultTreeModel)view.myTree.getModel()).setRoot(node);
  }

  public static void showHierarchyInfo(CompilerReferenceHierarchyTestInfo info, PsiElement element) {
    final InternalCompilerRefServiceView view = createViewTab(element);
    final DefaultMutableTreeNode node = info.asTree();
    node.setUserObject(element);
    ((DefaultTreeModel)view.myTree.getModel()).setRoot(node);
  }

  private static InternalCompilerRefServiceView createViewTab(PsiElement element) {
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
