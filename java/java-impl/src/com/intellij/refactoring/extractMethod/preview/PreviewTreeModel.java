// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.ParametrizedDuplicates;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Pavel.Dolgov
 */
class PreviewTreeModel extends DefaultTreeModel {
  private final DefaultMutableTreeNode myDuplicatesGroup;
  private final DefaultMutableTreeNode myMethodGroup;
  private final PatternNode myPatternNode;
  private boolean myValid;

  public PreviewTreeModel(@NotNull ExtractMethodProcessor processor) {
    super(new DefaultMutableTreeNode(""));
    setValidImpl(true);
    DefaultMutableTreeNode root = getRoot();

    myMethodGroup = new DefaultMutableTreeNode(RefactoringBundle.message("refactoring.extract.method.preview.group.method"));
    root.add(myMethodGroup);
    PsiMethod emptyMethod = processor.generateEmptyMethod(processor.getMethodName(), processor.getTargetClass());
    myMethodGroup.add(new MethodNode(emptyMethod)); // will be replaced in updateMethod()

    DefaultMutableTreeNode originalGroup =
      new DefaultMutableTreeNode(RefactoringBundle.message("refactoring.extract.method.preview.group.original"));
    root.add(originalGroup);
    PsiElement[] elements = processor.getElements();
    myPatternNode = new PatternNode(elements);
    originalGroup.add(myPatternNode);

    List<Match> duplicates = getDuplicates(processor);
    if (!ContainerUtil.isEmpty(duplicates)) {
      myDuplicatesGroup = new DefaultMutableTreeNode(RefactoringBundle.message("refactoring.extract.method.preview.group.duplicates"));
      root.add(myDuplicatesGroup);
      for (Match duplicate : duplicates) {
        myDuplicatesGroup.add(new DuplicateNode(duplicate));
      }
    }
    else {
      myDuplicatesGroup = null;
    }
  }

  @Override
  public DefaultMutableTreeNode getRoot() {
    return (DefaultMutableTreeNode)super.getRoot();
  }

  @NotNull
  MethodNode updateMethod(PsiMethod method) {
    myMethodGroup.removeAllChildren();
    MethodNode methodNode = new MethodNode(method);
    myMethodGroup.add(methodNode);
    reload(myMethodGroup);
    return methodNode;
  }

  @NotNull
  public List<DuplicateNode> getEnabledDuplicates() {
    if (myDuplicatesGroup != null && myDuplicatesGroup.getChildCount() != 0) {
      List<DuplicateNode> duplicates = new ArrayList<>();
      for (int i = 0; i < myDuplicatesGroup.getChildCount(); i++) {
        TreeNode node = myDuplicatesGroup.getChildAt(i);
        if (node instanceof DuplicateNode) {
          DuplicateNode duplicateNode = (DuplicateNode)node;
          if (!duplicateNode.isExcluded() && duplicateNode.isValid()) {
            duplicates.add(duplicateNode);
          }
        }
      }
      return duplicates;
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<DuplicateNode> getAllDuplicates() {
    if (myDuplicatesGroup != null && myDuplicatesGroup.getChildCount() != 0) {
      List<DuplicateNode> duplicates = new ArrayList<>();
      for (int i = 0; i < myDuplicatesGroup.getChildCount(); i++) {
        TreeNode node = myDuplicatesGroup.getChildAt(i);
        if (node instanceof DuplicateNode) {
          duplicates.add((DuplicateNode)node);
        }
      }
      return duplicates;
    }
    return Collections.emptyList();
  }

  @NotNull
  public PatternNode getPatternNode() {
    return myPatternNode;
  }

  public synchronized boolean isValid() {
    return myValid;
  }

  private synchronized void setValidImpl(boolean valid) {
    myValid = valid;
  }

  @Nullable
  public static List<Match> getDuplicates(@NotNull ExtractMethodProcessor processor) {
    List<Match> duplicates = processor.getDuplicates();
    if (ContainerUtil.isEmpty(duplicates)) {
      ParametrizedDuplicates parametrizedDuplicates = processor.getParametrizedDuplicates();
      if (parametrizedDuplicates != null) {
        duplicates = parametrizedDuplicates.getDuplicates();
      }
    }
    return duplicates;
  }

  public void setValid(boolean valid) {
    setValidImpl(valid);
    setValid(getRoot(), valid);
  }

  private void setValid(TreeNode node, boolean valid) {
    if (node instanceof FragmentNode) {
      ((FragmentNode)node).setValid(valid);
      reload(node);
    }
    if (!node.isLeaf()) {
      for (int i = 0; i < node.getChildCount(); i++) {
        setValid(node.getChildAt(i), valid);
      }
    }
  }
}
