// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.highlighter;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public final class EditorHighlighterFactoryImpl extends EditorHighlighterFactory {
  private static final Logger LOG = Logger.getInstance(EditorHighlighterFactoryImpl.class);

  @NotNull
  @Override
  public EditorHighlighter createEditorHighlighter(SyntaxHighlighter highlighter, @NotNull final EditorColorsScheme colors) {
    if (highlighter == null) {
      highlighter = new PlainSyntaxHighlighter();
    }
    return new LexerEditorHighlighter(highlighter, colors);
  }

  @NotNull
  @Override
  public EditorHighlighter createEditorHighlighter(@NotNull final FileType fileType, @NotNull final EditorColorsScheme settings, final Project project) {
    if (fileType instanceof LanguageFileType) {
      return FileTypeEditorHighlighterProviders.INSTANCE.forFileType(fileType).getEditorHighlighter(project, fileType, null, settings);
    }

    SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, null);
    return createEditorHighlighter(highlighter, settings);
  }

  @NotNull
  @Override
  public EditorHighlighter createEditorHighlighter(final Project project, @NotNull final FileType fileType) {
    return createEditorHighlighter(fileType, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  @NotNull
  @Override
  public EditorHighlighter createEditorHighlighter(@NotNull VirtualFile vFile, @NotNull EditorColorsScheme settings, @Nullable Project project) {
    FileType fileType = vFile.getFileType();
    if (fileType instanceof LanguageFileType) {
      Language substLang = project == null ? null : LanguageUtil.getLanguageForPsi(project, vFile, fileType);
      LanguageFileType substFileType = substLang != null && substLang != ((LanguageFileType)fileType).getLanguage() ?
                                       substLang.getAssociatedFileType() : null;
      if (substFileType != null) {
        EditorHighlighterProvider provider = FileTypeEditorHighlighterProviders.INSTANCE.forFileType(substFileType);
        EditorHighlighter editorHighlighter = provider.getEditorHighlighter(project, substFileType, vFile, settings);
        boolean isPlain = editorHighlighter.getClass() == LexerEditorHighlighter.class &&
                          ((LexerEditorHighlighter) editorHighlighter).isPlain();
        if (!isPlain) {
          return editorHighlighter;
        }
      }
      try {
        return FileTypeEditorHighlighterProviders.INSTANCE.forFileType(fileType).getEditorHighlighter(project, fileType, vFile, settings);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, vFile);
    return createEditorHighlighter(highlighter, settings);
  }

  @NotNull
  @Override
  public EditorHighlighter createEditorHighlighter(final Project project, @NotNull final VirtualFile file) {
    return createEditorHighlighter(file, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  @NotNull
  @Override
  public EditorHighlighter createEditorHighlighter(final Project project, @NotNull final String fileName) {
    return createEditorHighlighter(EditorColorsManager.getInstance().getGlobalScheme(), fileName, project);
  }

  @NotNull
  @Override
  public EditorHighlighter createEditorHighlighter(@NotNull final EditorColorsScheme settings, @NotNull final String fileName, @Nullable final Project project) {
    return createEditorHighlighter(new LightVirtualFile(fileName), settings, project);
  }
}
