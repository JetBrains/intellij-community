/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.ide.DataManager;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TemplateEditorUtil {
  private TemplateEditorUtil() {}

  public static Editor createEditor(boolean isReadOnly, CharSequence text) {
    return createEditor(isReadOnly, text, null);
  }

  public static Editor createEditor(boolean isReadOnly, CharSequence text, @Nullable TemplateContext context) {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    return createEditor(isReadOnly, createDocument(text, context, project), project);
  }

  private static Document createDocument(CharSequence text, @Nullable TemplateContext context, Project project) {
    if (context != null) {
      for (TemplateContextType type : TemplateManagerImpl.getAllContextTypes()) {
        if (context.isExplicitlyEnabled(type)) {
          return type.createDocument(text, project);
        }
      }
    }

    return EditorFactory.getInstance().createDocument(text);
  }

  private static Editor createEditor(boolean isReadOnly, final Document document, final Project project) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Editor editor = (isReadOnly ? editorFactory.createViewer(document, project) : editorFactory.createEditor(document, project));
    editor.getContentComponent().setFocusable(!isReadOnly);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setCaretRowShown(false);

    EditorColorsScheme scheme = editor.getColorsScheme();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file, scheme, project);
      ((EditorEx) editor).setHighlighter(highlighter);
      
    }

    return editor;
  }

  public static void setHighlighter(Editor editor, @Nullable TemplateContext templateContext) {
    SyntaxHighlighter highlighter = null;
    if (templateContext != null) {
      for(TemplateContextType type: TemplateManagerImpl.getAllContextTypes()) {
        if (templateContext.isEnabled(type)) {
          highlighter = type.createHighlighter();
          if (highlighter != null) break;
        }
      }
    }
    setHighlighter((EditorEx)editor, highlighter);
  }

  public static void setHighlighter(@NotNull Editor editor, @Nullable TemplateContextType templateContextType) {
    setHighlighter((EditorEx)editor, templateContextType != null ? templateContextType.createHighlighter() : null);
  }

  private static void setHighlighter(EditorEx editor, @Nullable SyntaxHighlighter highlighter) {
    EditorColorsScheme editorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    LayeredLexerEditorHighlighter layeredHighlighter = new LayeredLexerEditorHighlighter(new TemplateHighlighter(), editorColorsScheme);
    layeredHighlighter.registerLayer(TemplateTokenType.TEXT, new LayerDescriptor(ObjectUtils.notNull(highlighter, new PlainSyntaxHighlighter()), ""));
    editor.setHighlighter(layeredHighlighter);
  }

  private static class TemplateHighlighter extends SyntaxHighlighterBase {
    private final Lexer myLexer;

    public TemplateHighlighter() {
      myLexer = new MergingLexerAdapter(new TemplateTextLexer(), TokenSet.create(TemplateTokenType.TEXT));
    }

    @Override
    @NotNull
    public Lexer getHighlightingLexer() {
      return myLexer;
    }

    @Override
    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
      return tokenType == TemplateTokenType.VARIABLE ? pack(TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES) : EMPTY;
    }
  }
}
