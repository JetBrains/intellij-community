/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.util.PackageChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.treetable.TreeTable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public interface UIHelper {
  void installToolTipHandler(JTree tree);

  void installToolTipHandler(JTable table);

  void installEditSourceOnDoubleClick(JTree tree);

  void installEditSourceOnDoubleClick(TreeTable tree);

  void installEditSourceOnDoubleClick(Table table);

  void installTreeSpeedSearch(JTree tree);

  void installListSpeedSearch(JList list);

  void installEditSourceOnEnterKeyHandler(JTree tree);

  TableCellRenderer createPsiElementRenderer(PsiElement psiElement, Project project);

  TreeCellRenderer createHighlightableTreeCellRenderer();

  void drawDottedRectangle(Graphics g, int x, int y, int i, int i1);

  void installSmartExpander(JTree tree);

  void installSelectionSaver(JTree tree);

  TreeTable createDirectoryDiffTree(Project project, AbstractRevisions[] roots);

  /**
   * @param text
   * @param type expected type that the expression to be input in this field should have, subtypes accepted
   * @param context where the expression is to be virtually inserted (for smart completing variables from the context)
   * @param project
   */
  TextComponent createTypedTextField(final String text, PsiType type, PsiElement context, final Project project);

  interface CopyPasteSupport {
    CutProvider getCutProvider();
    CopyProvider getCopyProvider();
    PasteProvider getPasteProvider();
  }

  interface PsiElementSelector {
    PsiElement[] getSelectedElements();
  }

  CopyPasteSupport createPsiBasedCopyPasteSupport(Project project, JComponent keyReceiver, PsiElementSelector dataSelector);

  DeleteProvider createPsiBasedDeleteProvider();

  PackageChooser createPackageChooser(String title, Project project); 
}
