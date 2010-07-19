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
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.Table;
import com.intellij.ui.treeStructure.treetable.TreeTable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * @see com.intellij.peer.PeerFactory#getUIHelper()
 */
public interface UIHelper {
  /**
   * @deprecated use JBTree class instead, it will automatically configure tool tips
   */
  void installToolTipHandler(JTree tree);

  /**
   * @deprecated use JBTable class instead, it will automatically configure tool tips
   */
  void installToolTipHandler(JTable table);

  void installEditSourceOnDoubleClick(JTree tree);

  void installEditSourceOnDoubleClick(TreeTable tree);

  void installEditSourceOnDoubleClick(Table table);

  void installTreeTableSpeedSearch(TreeTable treeTable);

  void installTreeTableSpeedSearch(TreeTable treeTable, Convertor<TreePath, String> convertor);

  void installTreeSpeedSearch(JTree tree);

  void installTreeSpeedSearch(JTree tree, Convertor<TreePath, String> convertor);

  void installListSpeedSearch(JList list);

  void installListSpeedSearch(JList list, Function<Object, String> elementTextDelegate);

  void installEditSourceOnEnterKeyHandler(JTree tree);

  SplitterProportionsData createSplitterProportionsData();

  TableCellRenderer createPsiElementRenderer(PsiElement psiElement, Project project);

  TreeCellRenderer createHighlightableTreeCellRenderer();

  void drawDottedRectangle(Graphics g, int x, int y, int i, int i1);

  void installSmartExpander(JTree tree);

  void installSelectionSaver(JTree tree);

  /**
   * @param text
   * @param type expected type that the expression to be input in this field should have, subtypes accepted
   * @param context where the expression is to be virtually inserted (for smart completing variables from the context)
   * @param project
   */
  TextComponent createTypedTextField(final String text, PsiType type, PsiElement context, final Project project);

  PackageChooser createPackageChooser(String title, Project project);
}
