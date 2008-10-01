package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.testFramework.LightVirtualFile;

/**
 * @author yole
 */
public class EditorHighlighterFactoryImpl extends EditorHighlighterFactory {
  public EditorHighlighter createEditorHighlighter(SyntaxHighlighter highlighter, final EditorColorsScheme colors) {
    if (highlighter == null) highlighter = new PlainSyntaxHighlighter();
    return new LexerEditorHighlighter(highlighter, colors);
  }

  public EditorHighlighter createEditorHighlighter(final FileType fileType, final EditorColorsScheme settings, final Project project) {
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType)fileType).getEditorHighlighter(project, null, settings);
    }

    SyntaxHighlighter highlighter = SyntaxHighlighter.PROVIDER.create(fileType, project, null);
    return createEditorHighlighter(highlighter, settings);
  }

  public EditorHighlighter createEditorHighlighter(final Project project, final FileType fileType) {
    return createEditorHighlighter(fileType, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  public EditorHighlighter createEditorHighlighter(final VirtualFile vFile, final EditorColorsScheme settings, final Project project) {
    final FileType fileType = vFile.getFileType();
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType)fileType).getEditorHighlighter(project, vFile, settings);
    }

    final ContentBasedSyntaxHighlighterProvider[] providers = Extensions.getExtensions(ContentBasedSyntaxHighlighterProvider.EP_NAME);
    SyntaxHighlighter highlighter = null;
    for (ContentBasedSyntaxHighlighterProvider provider : providers) {
      if (provider.isApplicable(fileType, project, vFile)) {
        highlighter = provider.createHighlighter(fileType, project, vFile);
      }
    }
    if (highlighter == null) {
      highlighter = SyntaxHighlighter.PROVIDER.create(fileType, project, vFile);
    }
    return createEditorHighlighter(highlighter, settings);
  }

  public EditorHighlighter createEditorHighlighter(final Project project, final VirtualFile file) {
    return createEditorHighlighter(file, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  public EditorHighlighter createEditorHighlighter(final Project project, final String fileName) {
    return createEditorHighlighter(EditorColorsManager.getInstance().getGlobalScheme(), fileName, project);
  }

  public EditorHighlighter createEditorHighlighter(final EditorColorsScheme settings, final String fileName, final Project project) {
    return createEditorHighlighter(new LightVirtualFile(fileName), settings, project);
  }
}