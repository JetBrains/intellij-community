// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

/**
 * On Ctrl-Shift-F7 on the class reference in the method throws clause, highlight places throwing this exception inside the method
 * (or just highlight the usages under the caret if the user told us so)
 */
class HighlightThrowsClassesHandler extends HighlightExceptionsHandler {
  private @NotNull final Editor myEditor;
  private @NotNull final PsiFile myFile;
  private final PsiElement myResolved;

  enum MODE {
    SHOW_USAGES,  // usual boring usages of the class under the caret
    SHOW_THROWING_PLACES // finds places, e.g. method calls which could throw exception of this class
  }

  HighlightThrowsClassesHandler(@NotNull Editor editor,
                                @NotNull PsiFile file,
                                @NotNull PsiElement target,
                                @NotNull PsiClassType type,
                                @NotNull PsiElement block,
                                @NotNull PsiElement resolved) {
    super(editor, file, target, new PsiClassType[]{type}, block, null, __->true);
    myEditor = editor;
    myFile = file;
    myResolved = resolved;
  }

  @Override
  public void highlightUsages() {
    // ask user whether she wants to highlight target exception usages or places that throws that exception
    String className = "'" + ((PsiClass)myResolved).getName() + "'";
    String throwingPlacesMode = JavaBundle.message("highlight.throws.popup.throwing.places", className);
    String showUsagesMode = JavaBundle.message("highlight.throws.popup.usages", className);
    IPopupChooserBuilder<String> builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(Arrays.asList(throwingPlacesMode, showUsagesMode))
      .setRenderer(new SimpleListCellRenderer<String>(){
        @Override
        public void customize(@NotNull JList<? extends String> list, String value, int index, boolean selected, boolean hasFocus) {
          setIcon(showUsagesMode.equals(value) ? AllIcons.Nodes.Class : AllIcons.Nodes.ExceptionClass);
          setText(value);
        }
      })
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setItemChosenCallback(selected -> {
        MODE mode = selected.equals(showUsagesMode) ? MODE.SHOW_USAGES : MODE.SHOW_THROWING_PLACES;
        if (mode == MODE.SHOW_THROWING_PLACES) {
          super.highlightUsages();
        }
        else {
          new PsiElement2UsageTargetAdapter(myResolved).highlightUsages(myFile, myEditor, HighlightUsagesHandler.isClearHighlights(myEditor));
        }
      })
      .setTitle(JavaBundle.message("highlight.throws.class.name", className));
    builder.createPopup().showInBestPositionFor(myEditor);
  }
}
