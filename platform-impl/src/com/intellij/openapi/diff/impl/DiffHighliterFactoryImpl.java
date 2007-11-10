package com.intellij.openapi.diff.impl;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;

public class DiffHighliterFactoryImpl implements DiffHighliterFactory {
  private final Project myProject;
  private final FileType myFileType;

  public DiffHighliterFactoryImpl(FileType fileType, Project project) {
    myFileType = fileType;
    myProject = project;
  }

  public EditorHighlighter createHighlighter() {
    return (myFileType == null || myProject == null) ?
        null : HighlighterFactory.createHighlighter(myProject, myFileType);
  }
}
