/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ui;

import com.intellij.util.ui.Tree;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public interface UIHelper {
  void installToolTipHandler(JTree tree);

  void installToolTipHandler(JTable table);

  void installEditSourceOnDoubleClick(JTree tree);

  void installEditSourceOnDoubleClick(TreeTable tree);

  void installEditSourceOnDoubleClick(Table table);

  void installTreeSpeedSearch(Tree tree);

  void installEditSourceOnEnterKeyHandler(JTree tree);

  TableCellRenderer createPsiElementRenderer(PsiElement psiElement, Project project);

  TreeCellRenderer createHighlightableTreeCellRenderer();

  void drawDottedRectangle(Graphics g, int x, int y, int i, int i1);

  void showListPopup(String title, JList list, Runnable finishAction, Project project, int x, int y);

  void installSmartExpander(JTree tree);

  void installSelectionSaver(JTree tree);
}
