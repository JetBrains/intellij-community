/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.lexer.CompositeLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class TemplateEditorUtil {
  private TemplateEditorUtil() {}

  public static Editor createEditor(boolean isReadOnly, CharSequence text) {
    return createEditor(isReadOnly, text, null);
  }

  public static Editor createEditor(boolean isReadOnly, CharSequence text, @Nullable Map<TemplateContextType, Boolean> context) {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    return createEditor(isReadOnly, createDocument(text, context, project), project);
  }

  private static Document createDocument(CharSequence text, @Nullable Map<TemplateContextType, Boolean> context, Project project) {
    if (context != null) {
      for (Map.Entry<TemplateContextType, Boolean> entry : context.entrySet()) {
        if (entry.getValue()) {
          return entry.getKey().createDocument(text, project);
        }
      }
    }

    return EditorFactory.getInstance().createDocument(text);
  }

  private static Editor createEditor(boolean isReadOnly, final Document document, final Project project) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Editor editor = (isReadOnly ? editorFactory.createViewer(document, project) : editorFactory.createEditor(document, project));

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);

    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file, scheme, project);
      ((EditorEx) editor).setHighlighter(highlighter);
    }

    return editor;
  }

  public static void setHighlighter(Editor editor, TemplateContext templateContext) {
    SyntaxHighlighter baseHighlighter = null;
    for(TemplateContextType type: TemplateManagerImpl.getAllContextTypes()) {
      if (templateContext.isEnabled(type)) {
        baseHighlighter = type.createHighlighter();
        if (baseHighlighter != null) break;
      }
    }
    if (baseHighlighter == null) {
      baseHighlighter = new PlainSyntaxHighlighter();
    }

    SyntaxHighlighter highlighter = createTemplateTextHighlighter(baseHighlighter);
    ((EditorEx)editor).setHighlighter(new LexerEditorHighlighter(highlighter, EditorColorsManager.getInstance().getGlobalScheme()));
  }

  private final static TokenSet TOKENS_TO_MERGE = TokenSet.create(TemplateTokenType.TEXT);

  private static SyntaxHighlighter createTemplateTextHighlighter(final SyntaxHighlighter original) {
    return new TemplateHighlighter(original);
  }

  private static class TemplateHighlighter extends SyntaxHighlighterBase {
    private final Lexer myLexer;
    private final SyntaxHighlighter myOriginalHighlighter;

    public TemplateHighlighter(SyntaxHighlighter original) {
      myOriginalHighlighter = original;
      Lexer originalLexer = original.getHighlightingLexer();
      Lexer templateLexer = new TemplateTextLexer();
      templateLexer = new MergingLexerAdapter(templateLexer, TOKENS_TO_MERGE);

      myLexer = new CompositeLexer(originalLexer, templateLexer) {
        @Override
        protected IElementType getCompositeTokenType(IElementType type1, IElementType type2) {
          if (type2 == TemplateTokenType.VARIABLE) {
            return type2;
          }
          else {
            return type1;
          }
        }
      };
    }

    @Override
    @NotNull
    public Lexer getHighlightingLexer() {
      return myLexer;
    }

    @Override
    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
      if (tokenType == TemplateTokenType.VARIABLE) {
        return pack(myOriginalHighlighter.getTokenHighlights(tokenType), TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES);
      }

      return myOriginalHighlighter.getTokenHighlights(tokenType);
    }
  }
}
