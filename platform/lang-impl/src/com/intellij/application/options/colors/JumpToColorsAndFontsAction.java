/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.colors;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.application.options.colors.ColorAndFontOptions.selectOrEditColor;
import static com.intellij.ui.SimpleTextAttributes.*;

/**
 * @author gregsh
 */
public class JumpToColorsAndFontsAction extends DumbAwareAction {

  public JumpToColorsAndFontsAction() {
    setInjectedContext(true);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    e.getPresentation().setEnabledAndVisible(project != null && editor != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    // todo handle ColorKey's as well
    Project project = e.getData(CommonDataKeys.PROJECT);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (project == null || editor == null) return;
    Map<TextAttributesKey, Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor>> keyMap = ContainerUtil.newHashMap();
    Processor<RangeHighlighterEx> processor = r -> {
      Object tt = r.getErrorStripeTooltip();
      TextAttributesKey key = tt instanceof HighlightInfo ? ObjectUtils.chooseNotNull(
        ((HighlightInfo)tt).forcedTextAttributesKey, ((HighlightInfo)tt).type.getAttributesKey()) : null;
      Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> p =
        key == null ? null : ColorSettingsPages.getInstance().getAttributeDescriptor(key);
      if (p != null) keyMap.put(key, p);
      return true;
    };
    JBIterable<Editor> editors = editor instanceof EditorWindow ? JBIterable.of(editor, ((EditorWindow)editor).getDelegate()) : JBIterable.of(editor);
    for (Editor ed : editors) {
      TextRange selection = EditorUtil.getSelectionInAnyMode(ed);
      MarkupModel forDocument = DocumentMarkupModel.forDocument(ed.getDocument(), project, false);
      if (forDocument != null) {
        ((MarkupModelEx)forDocument).processRangeHighlightersOverlappingWith(selection.getStartOffset(), selection.getEndOffset(), processor);
      }
      ((MarkupModelEx)ed.getMarkupModel()).processRangeHighlightersOverlappingWith(selection.getStartOffset(), selection.getEndOffset(), processor);
      EditorHighlighter highlighter = ed instanceof EditorEx ? ((EditorEx)ed).getHighlighter() : null;
      SyntaxHighlighter syntaxHighlighter = highlighter instanceof LexerEditorHighlighter ? ((LexerEditorHighlighter)highlighter).getSyntaxHighlighter() : null;
      if (syntaxHighlighter != null) {
        HighlighterIterator iterator = highlighter.createIterator(selection.getStartOffset());
        while (!iterator.atEnd()) {
          for (TextAttributesKey key : syntaxHighlighter.getTokenHighlights(iterator.getTokenType())) {
            Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> p =
              key == null ? null : ColorSettingsPages.getInstance().getAttributeDescriptor(key);
            if (p != null) keyMap.put(key, p);
          }
          if (iterator.getEnd() >= selection.getEndOffset()) break;
          iterator.advance();
        }
      }
    }

    if (keyMap.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, "No text attributes found");
    }
    else if (keyMap.size() == 1) {
      Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> p = keyMap.values().iterator().next();
      if (!openSettingsAndSelectKey(project, p.first, p.second)) {
        HintManager.getInstance().showErrorHint(editor, "No appropriate settings page found");
      }
    }
    else {
      ArrayList<Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor>> attrs = ContainerUtil.newArrayList(keyMap.values());
      Collections.sort(attrs, (o1, o2) -> StringUtil.naturalCompare(
        o1.first.getDisplayName() + o1.second.getDisplayName(), o2.first.getDisplayName() + o2.second.getDisplayName()));

      EditorColorsScheme colorsScheme = editor.getColorsScheme();
      JBList<Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor>> list = new JBList<>(attrs);
      list.setCellRenderer(new ColoredListCellRenderer<Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor>>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor>> list,
                                             Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> value,
                                             int index,
                                             boolean selected,
                                             boolean hasFocus) {
          TextAttributes ta = colorsScheme.getAttributes(value.second.getKey());
          Color fg = ObjectUtils.chooseNotNull(ta.getForegroundColor(), colorsScheme.getDefaultForeground());
          Color bg = ObjectUtils.chooseNotNull(ta.getBackgroundColor(), colorsScheme.getDefaultBackground());
          SimpleTextAttributes sa = fromTextAttributes(ta);
          SimpleTextAttributes saOpaque = sa.derive(STYLE_OPAQUE | sa.getStyle(), fg, bg, null);
          SimpleTextAttributes saSelected = REGULAR_ATTRIBUTES.derive(sa.getStyle(), null, null, null);
          SimpleTextAttributes saCur = REGULAR_ATTRIBUTES;
          List<String> split = StringUtil.split(value.first.getDisplayName() + "//" + value.second.getDisplayName(), "//");
          for (int i = 0, len = split.size(); i < len; i++) {
            boolean last = i == len - 1;
            saCur = !last ? REGULAR_ATTRIBUTES : selected ? saSelected : saOpaque;
            if (last) append(" ", saCur);
            append(split.get(i), saCur);
            if (last) append(" ", saCur);
            else append(" > ", GRAYED_ATTRIBUTES);
          }
          Color stripeColor = ta.getErrorStripeColor();
          boolean addStripe = stripeColor != null && stripeColor != saCur.getBgColor();
          boolean addBoxed = ta.getEffectType() == EffectType.BOXED && ta.getEffectColor() != null;
          if (addBoxed) {
            append("\u25A2" + (addStripe ? "" : " "), saCur.derive(-1, ta.getEffectColor(), null, null));
          }
          if (addStripe) {
            append(" ", saCur.derive(STYLE_OPAQUE, null, stripeColor, null));
          }
        }
      });
      JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle(StringUtil.notNullize(e.getPresentation().getText()))
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(() -> {
          Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> p = list.getSelectedValue();
          if (p != null && !openSettingsAndSelectKey(project, p.first, p.second)) {
            HintManager.getInstance().showErrorHint(editor, "No appropriate settings page found");
          }
        })
        .createPopup().showInBestPositionFor(editor);
    }
  }

  private static boolean openSettingsAndSelectKey(@NotNull Project project, @NotNull ColorAndFontDescriptorsProvider page, @NotNull AttributesDescriptor descriptor) {
    return selectOrEditColor(id -> CommonDataKeys.PROJECT.is(id) ? project : null, descriptor.getDisplayName(), page.getDisplayName());
  }
}
