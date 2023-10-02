// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.highlighter;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class EditorHighlighterFactoryImpl extends EditorHighlighterFactory {
  private static final Logger LOG = Logger.getInstance(EditorHighlighterFactoryImpl.class);

  @Override
  public @NotNull EditorHighlighter createEditorHighlighter(SyntaxHighlighter highlighter, final @NotNull EditorColorsScheme colors) {
    if (highlighter == null) {
      highlighter = new PlainSyntaxHighlighter();
    }
    return new LexerEditorHighlighter(highlighter, colors);
  }

  @Override
  public @NotNull EditorHighlighter createEditorHighlighter(final @NotNull FileType fileType, final @NotNull EditorColorsScheme settings, final Project project) {
    if (fileType instanceof LanguageFileType) {
      return FileTypeEditorHighlighterProviders.INSTANCE.forFileType(fileType).getEditorHighlighter(project, fileType, null, settings);
    }

    SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, null);
    return createEditorHighlighter(highlighter, settings);
  }

  @Override
  public @NotNull EditorHighlighter createEditorHighlighter(final Project project, final @NotNull FileType fileType) {
    return createEditorHighlighter(fileType, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  @Override
  public @NotNull EditorHighlighter createEditorHighlighter(@NotNull VirtualFile vFile, @NotNull EditorColorsScheme settings, @Nullable Project project) {
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
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-333907, EA-821093")) {
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

  @Override
  public @NotNull EditorHighlighter createEditorHighlighter(final Project project, final @NotNull VirtualFile file) {
    return createEditorHighlighter(file, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  @Override
  public @NotNull EditorHighlighter createEditorHighlighter(final Project project, final @NotNull String fileName) {
    return createEditorHighlighter(EditorColorsManager.getInstance().getGlobalScheme(), fileName, project);
  }

  @Override
  public @NotNull EditorHighlighter createEditorHighlighter(final @NotNull EditorColorsScheme settings, final @NotNull String fileName, final @Nullable Project project) {
    return createEditorHighlighter(new LightVirtualFile(fileName), settings, project);
  }
}
