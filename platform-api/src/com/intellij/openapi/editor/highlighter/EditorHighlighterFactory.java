package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public abstract class EditorHighlighterFactory {

  public static EditorHighlighterFactory getInstance() {
    return ServiceManager.getService(EditorHighlighterFactory.class);
  }

  public abstract EditorHighlighter createEditorHighlighter(final SyntaxHighlighter syntaxHighlighter, final EditorColorsScheme colors);

  public abstract EditorHighlighter createEditorHighlighter(final FileType fileType, final EditorColorsScheme settings, final Project project);

  public abstract EditorHighlighter createEditorHighlighter(final Project project, final FileType fileType);

  public abstract EditorHighlighter createEditorHighlighter(final VirtualFile file, final EditorColorsScheme globalScheme, final Project project);

  public abstract EditorHighlighter createEditorHighlighter(final Project project, final VirtualFile file);

  public abstract EditorHighlighter createEditorHighlighter(final Project project, final String fileName);

  public abstract EditorHighlighter createEditorHighlighter(final EditorColorsScheme settings, final String fileName, final Project project);
}