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
package com.intellij.refactoring;

import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.introduce.IntroduceTarget;
import com.intellij.refactoring.introduce.PsiIntroduceTarget;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class IntroduceTargetChooser {
  private IntroduceTargetChooser() {
  }

  public static <T extends PsiElement> void showChooser(@NotNull Editor editor,
                                                        @NotNull List<T> expressions,
                                                        @NotNull Pass<T> callback,
                                                        @NotNull Function<T, String> renderer) {
    showChooser(editor, expressions, callback, renderer, "Expressions");
  }

  public static <T extends PsiElement> void showChooser(@NotNull Editor editor,
                                                        @NotNull List<T> expressions,
                                                        @NotNull Pass<T> callback,
                                                        @NotNull Function<T, String> renderer,
                                                        @NotNull @Nls String title) {
    showChooser(editor, expressions, callback, renderer, title, ScopeHighlighter.NATURAL_RANGER);
  }

  public static <T extends PsiElement> void showChooser(@NotNull Editor editor,
                                                        @NotNull List<T> expressions,
                                                        @NotNull Pass<T> callback,
                                                        @NotNull Function<T, String> renderer,
                                                        @NotNull @Nls String title,
                                                        @NotNull NotNullFunction<PsiElement, TextRange> ranger) {
    showChooser(editor, expressions, callback, renderer, title, -1, ranger);
  }

  public static <T extends PsiElement> void showChooser(@NotNull Editor editor,
                                                        @NotNull List<T> expressions,
                                                        @NotNull Pass<T> callback,
                                                        @NotNull Function<T, String> renderer,
                                                        @NotNull @Nls String title,
                                                        int selection,
                                                        @NotNull NotNullFunction<PsiElement, TextRange> ranger) {
    List<MyIntroduceTarget<T>> targets = ContainerUtil.map(expressions, t -> new MyIntroduceTarget<>(t, ranger, renderer));
    Pass<MyIntroduceTarget<T>> callbackWrapper = new Pass<MyIntroduceTarget<T>>() {
      @Override
      public void pass(MyIntroduceTarget<T> target) {
        callback.pass(target.getPlace());
      }
    };
    showIntroduceTargetChooser(editor, targets, callbackWrapper, title, selection);
  }

  public static <T extends IntroduceTarget> void showIntroduceTargetChooser(@NotNull Editor editor,
                                                                            @NotNull List<T> expressions,
                                                                            @NotNull Pass<T> callback,
                                                                            @NotNull @Nls String title,
                                                                            int selection) {


    final ScopeHighlighter highlighter = new ScopeHighlighter(editor);
    final DefaultListModel model = new DefaultListModel();
    for (T expr : expressions) {
      model.addElement(expr);
    }
    final JList list = new JBList(model);
    // Set the accessible name so that screen readers announce the list tile (e.g. "Expression Types list").
    AccessibleContextUtil.setName(list, title);
    list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    if (selection > -1) list.setSelectedIndex(selection);
    list.setCellRenderer(new DefaultListCellRenderer() {

      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final IntroduceTarget expr = (T)value;
        if (expr.isValid()) {
          String text = expr.render();
          int firstNewLinePos = text.indexOf('\n');
          String trimmedText = text.substring(0, firstNewLinePos != -1 ? firstNewLinePos : Math.min(100, text.length()));
          if (trimmedText.length() != text.length()) trimmedText += " ...";
          setText(trimmedText);
        }
        else {
          setForeground(JBColor.RED);
          setText("Invalid");
        }
        return rendererComponent;
      }
    });

    list.addListSelectionListener(e -> {
      highlighter.dropHighlight();
      final int index = list.getSelectedIndex();
      if (index < 0) return;
      final T expr = (T)model.get(index);
      if (expr.isValid()) {
        TextRange range = expr.getTextRange();
        highlighter.highlight(Pair.create(range, Collections.singletonList(range)));
      }
    });

    JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setTitle(title)
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChoosenCallback(() -> {
        T expr = (T)list.getSelectedValue();
        if (expr != null && expr.isValid()) {
          callback.pass(expr);
        }
      })
      .addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          highlighter.dropHighlight();
        }
      })
      .createPopup().showInBestPositionFor(editor);
  }

  private static class MyIntroduceTarget<T extends PsiElement> extends PsiIntroduceTarget<T> {
    private final NotNullFunction<PsiElement, TextRange> myRanger;
    private final Function<T, String> myRenderer;

    public MyIntroduceTarget(@NotNull T psi,
                             @NotNull NotNullFunction<PsiElement, TextRange> ranger,
                             @NotNull Function<T, String> renderer) {
      super(psi);
      myRanger = ranger;
      myRenderer = renderer;
    }

    @NotNull
    @Override
    public TextRange getTextRange() {
      return myRanger.fun(getPlace());
    }

    @NotNull
    @Override
    public String render() {
      return myRenderer.fun(getPlace());
    }
  }
}
