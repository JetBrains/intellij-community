// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.util.containers.ContainerUtil;

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
  private DefaultMutableTreeNode myDuplicatesGroup;

  public PreviewTreeModel(ExtractMethodProcessor processor) {
    super(new DefaultMutableTreeNode(""));
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)getRoot();

    DefaultMutableTreeNode methodGroup = new DefaultMutableTreeNode("Method to extract");
    root.add(methodGroup);
    methodGroup.add(new MethodNode(processor.generateEmptyMethod(processor.getMethodName(), processor.getTargetClass())));

    DefaultMutableTreeNode originalGroup = new DefaultMutableTreeNode("Original code fragment");
    root.add(originalGroup);
    PsiElement[] elements = processor.getElements();
    originalGroup.add(new OriginalNode(elements));

    List<Match> duplicates = processor.getDuplicates();
    if (!ContainerUtil.isEmpty(duplicates)) {
      myDuplicatesGroup = new DefaultMutableTreeNode("Duplicate code fragments");
      root.add(myDuplicatesGroup);
      for (Match duplicate : duplicates) {
        myDuplicatesGroup.add(new DuplicateNode(duplicate));
      }
    }
  }

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
}
