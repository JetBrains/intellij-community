// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EditorSelectionLocalSearchScope extends RangeBasedLocalSearchScope {
  private final Editor myEditor;
  private final Project myProject;
  private PsiElement[] myPsiElements;
  private VirtualFile[] myVirtualFiles; // only ever 0 or 1 elements long
  private TextRange[] myRanges;

  private void initVirtualFilesAndRanges() {
    if (myRanges != null) {
      return;
    }
    SelectionModel selectionModel = myEditor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      myVirtualFiles = VirtualFile.EMPTY_ARRAY;
      myRanges = TextRange.EMPTY_ARRAY;
      return;
    }

    myVirtualFiles = new VirtualFile[]{FileDocumentManager.getInstance().getFile(myEditor.getDocument())};

    int[] selectionStarts = selectionModel.getBlockSelectionStarts();
    int[] selectionEnds = selectionModel.getBlockSelectionEnds();
    myRanges = new TextRange[selectionStarts.length];
    for (int i = 0; i < selectionStarts.length; ++i) {
      myRanges[i] = new TextRange(selectionStarts[i], selectionEnds[i]);
    }
  }

  private void init() {
    ReadAction.run(() -> {
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
      if (psiFile == null) {
        myPsiElements = PsiElement.EMPTY_ARRAY;
        return;
      }

      SelectionModel selectionModel = myEditor.getSelectionModel();
      if (!selectionModel.hasSelection()) {
        myPsiElements = PsiElement.EMPTY_ARRAY;
        return;
      }

      int[] selectionStarts = selectionModel.getBlockSelectionStarts();
      int[] selectionEnds = selectionModel.getBlockSelectionEnds();
      final List<@NotNull PsiElement> elements = new ArrayList<>();

      for (int i = 0; i < selectionStarts.length; ++i) {
        collectPsiElementsAtRange(psiFile, elements, selectionStarts[i], selectionEnds[i]);
      }

      myPsiElements = elements.toArray(PsiElement.EMPTY_ARRAY);
    });
  }

  @Override
  protected @NotNull PsiElement @NotNull [] getPsiElements() {
    if (myPsiElements == null) init();
    return myPsiElements;
  }

  private @NotNull TextRange @NotNull [] getRanges() {
    initVirtualFilesAndRanges();
    return myRanges;
  }

  public EditorSelectionLocalSearchScope(@NotNull Editor editor, Project project,
                                         @NotNull final @Nls String displayName) {
    this(editor, project, displayName, false);
  }

  public EditorSelectionLocalSearchScope(@NotNull Editor editor, Project project,
                                         @NotNull final @Nls String displayName,
                                         final boolean ignoreInjectedPsi) {
    super(displayName, ignoreInjectedPsi);
    myEditor = editor;
    myProject = project;
  }

  // Do not instantiate LocalSearchScope for getVirtualFiles, calcHashCode, equals, toString and containsRange.
  @Override
  public VirtualFile @NotNull [] getVirtualFiles() {
    initVirtualFilesAndRanges();
    return myVirtualFiles;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EditorSelectionLocalSearchScope)) return false;
    EditorSelectionLocalSearchScope other = (EditorSelectionLocalSearchScope)o;

    VirtualFile[] files = getVirtualFiles();
    VirtualFile[] otherFiles = other.getVirtualFiles();
    if (!Comparing.equal(files.length, otherFiles.length)) return false;
    if (files.length > 0) {
      if (!Comparing.equal(files[0], otherFiles[0])) return false;
    }

    TextRange[] ranges = getRanges();
    TextRange[] otherRanges = other.getRanges();
    if (ranges.length != otherRanges.length) return false;

    for (int i = 0; i < ranges.length; ++i) {
      if (!Comparing.equal(ranges[i], otherRanges[i])) return false;
    }

    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    TextRange[] ranges = getRanges();
    VirtualFile[] files = getVirtualFiles();
    if (files.length > 0) {
      builder.append(files[0].toString());
    }
    for (int i = 0; i < ranges.length; ++i) {
      if (i > 0) builder.append(',');
      builder.append('{');
      builder.append(ranges[i].toString());
      builder.append('}');
    }

    return builder.toString();
  }

  @Override
  protected int calcHashCode() {
    int result = 0;
    TextRange[] ranges = getRanges();
    VirtualFile[] files = getVirtualFiles();
    if (files.length > 0) {
      result += files[0].hashCode();
    }
    for (TextRange range : ranges) {
      result += range.hashCode();
    }
    return result;
  }

  @Override
  public boolean containsRange(@NotNull PsiFile file, @NotNull TextRange range) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) virtualFile = file.getNavigationElement().getContainingFile().getVirtualFile();
    VirtualFile[] files = getVirtualFiles();
    if (files.length == 0) return false;
    if (!files[0].equals(virtualFile)) return false;

    TextRange[] ranges = getRanges();
    for (TextRange textRange : ranges) {
      if (textRange.contains(range)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public @NotNull TextRange @NotNull [] getRanges(@NotNull VirtualFile file) {
    VirtualFile[] files = getVirtualFiles();
    if (files.length == 1 && files[0].equals(file)) {
      return getRanges();
    }

    return TextRange.EMPTY_ARRAY;
  }
}
