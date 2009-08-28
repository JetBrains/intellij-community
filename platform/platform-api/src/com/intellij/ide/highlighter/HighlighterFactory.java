package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class HighlighterFactory {
  private HighlighterFactory() {}

  public static EditorHighlighter createHighlighter(SyntaxHighlighter highlighter, EditorColorsScheme settings) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(highlighter, settings);
  }

  public static EditorHighlighter createHighlighter(Project project, String fileName) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileName);
  }

  public static EditorHighlighter createHighlighter(Project project, VirtualFile file) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file);
  }

  public static EditorHighlighter createHighlighter(Project project, FileType fileType) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType);
  }

  public static EditorHighlighter createHighlighter(EditorColorsScheme settings, String fileName, Project project) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(settings, fileName, project);
  }

  public static EditorHighlighter createHighlighter(FileType fileType, EditorColorsScheme settings, Project project) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, settings, project);
  }

  public static EditorHighlighter createHighlighter(VirtualFile vFile, EditorColorsScheme settings, Project project) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(vFile, settings, project);
  }
}