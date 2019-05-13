// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scopeView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.scopeView.nodes.ClassNode;
import com.intellij.ide.scopeView.nodes.FieldNode;
import com.intellij.ide.scopeView.nodes.MethodNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.DependencyNodeComparator;
import com.intellij.packageDependencies.ui.DirectoryNode;
import com.intellij.packageDependencies.ui.FileNode;
import com.intellij.psi.*;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.tree.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassesScopeTreeStructureExpander implements ScopeTreeStructureExpander {

  private final Project myProject;

  public ClassesScopeTreeStructureExpander(final Project project) {
    myProject = project;
  }

  @Override
  public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
    if (myProject.isDisposed()) return;
    ProjectView projectView = ProjectView.getInstance(myProject);
    final TreePath path = event.getPath();
    if (path == null) return;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    if (node instanceof DirectoryNode) {
      Set<ClassNode> classNodes = null;
      for (int i = node.getChildCount() - 1; i >= 0; i--) {
        final TreeNode childNode = node.getChildAt(i);
        if (childNode instanceof FileNode) {
          final FileNode fileNode = (FileNode)childNode;
          final PsiElement file = fileNode.getPsiElement();
          if (file instanceof PsiJavaFile) {
            final VirtualFile virtualFile = ((PsiJavaFile)file).getVirtualFile();
            if (virtualFile == null || (virtualFile.getFileType() != StdFileTypes.JAVA && virtualFile.getFileType() != StdFileTypes.CLASS)) {
              return;
            }
            final PsiClass[] psiClasses = ((PsiJavaFile)file).getClasses();
            if (psiClasses.length > 0) {
              if (classNodes == null) {
                classNodes = new HashSet<>();
              }

              for (final PsiClass psiClass : psiClasses) {
                if (psiClass != null && psiClass.isValid()) {
                  final ClassNode classNode = new ClassNode(psiClass);
                  classNodes.add(classNode);
                  if (projectView.isShowMembers(ScopeViewPane.ID)) {
                    final List<PsiElement> result = new ArrayList<>();
                    PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(psiClass, result);
                    for (PsiElement psiElement : result) {
                      psiElement.accept(new JavaElementVisitor() {
                        @Override public void visitClass(PsiClass aClass) {
                          classNode.add(new ClassNode(aClass));
                        }

                        @Override public void visitMethod(PsiMethod method) {
                          classNode.add(new MethodNode(method));
                        }

                        @Override public void visitField(PsiField field) {
                          classNode.add(new FieldNode(field));
                        }
                      });
                    }
                  }
                }
              }
              node.remove(fileNode);
            }
          }
        }
      }
      if (classNodes != null) {
        for (ClassNode classNode : classNodes) {
          node.add(classNode);
        }
      }
      TreeUtil.sort(node, getNodeComparator());
      final Object source = event.getSource();
      if (source instanceof JTree) {
        ((DefaultTreeModel)((JTree)source).getModel()).reload(node);
      }
    }
  }

  @Override
  public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
    final TreePath path = event.getPath();
    if (path == null) return;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    if (node instanceof DirectoryNode) {
      Set<FileNode> fileNodes = null;
      for (int i = node.getChildCount() - 1; i >= 0; i--) {
        final TreeNode childNode = node.getChildAt(i);
        if (childNode instanceof ClassNode) {
          final ClassNode classNode = (ClassNode)childNode;
          final PsiFile containingFile = classNode.getContainingFile();
          if (containingFile != null && containingFile.isValid()) {
            if (fileNodes == null) {
              fileNodes = new HashSet<>();
            }
            fileNodes.add(new FileNode(containingFile.getVirtualFile(), myProject, true));
          }
          node.remove(classNode);
        }
      }
      if (fileNodes != null) {
        for (FileNode fileNode : fileNodes) {
          node.add(fileNode);
        }
      }
      TreeUtil.sort(node, getNodeComparator());
      final Object source = event.getSource();
      if (source instanceof JTree) {
        ((DefaultTreeModel)((JTree)source).getModel()).reload(node);
      }
    }
  }

  private DependencyNodeComparator getNodeComparator() {
    return new DependencyNodeComparator(ProjectView.getInstance(myProject).isSortByType(ScopeViewPane.ID));
  }

}
