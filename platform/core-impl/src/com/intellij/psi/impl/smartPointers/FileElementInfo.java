// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class FileElementInfo extends SmartPointerElementInfo {
  @NotNull
  private final VirtualFile myVirtualFile;
  @NotNull
  private final Project myProject;
  @NotNull
  private final String myLanguageId;
  @NotNull
  private final String myFileClassName;

  FileElementInfo(@NotNull final PsiFile file) {
    myVirtualFile = file.getViewProvider().getVirtualFile();
    myProject = file.getProject();
    myLanguageId = LanguageUtil.getRootLanguage(file).getID();
    myFileClassName = file.getClass().getName();
  }

  @Override
  PsiElement restoreElement(@NotNull SmartPointerManagerImpl manager) {
    Language language = Language.findLanguageByID(myLanguageId);
    if (language == null) return null;
    PsiFile file = SelfElementInfo.restoreFileFromVirtual(myVirtualFile, myProject, language);
    return file != null && file.getClass().getName().equals(myFileClassName) ? file : null;
  }

  @Override
  PsiFile restoreFile(@NotNull SmartPointerManagerImpl manager) {
    PsiElement element = restoreElement(manager);
    return element == null ? null : element.getContainingFile(); // can be directory
  }

  @Override
  int elementHashCode() {
    return myVirtualFile.hashCode();
  }

  @Override
  boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other,
                                   @NotNull SmartPointerManagerImpl manager) {
    return other instanceof FileElementInfo && Comparing.equal(myVirtualFile, ((FileElementInfo)other).myVirtualFile);
  }

  @NotNull
  @Override
  VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  Segment getRange(@NotNull SmartPointerManagerImpl manager) {
    if (!myVirtualFile.isValid()) return null;

    Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
    return document == null ? null : TextRange.from(0, document.getTextLength());
  }

  @Nullable
  @Override
  Segment getPsiRange(@NotNull SmartPointerManagerImpl manager) {
    Document currentDoc = FileDocumentManager.getInstance().getCachedDocument(myVirtualFile);
    Document committedDoc = currentDoc == null ? null :
                                  ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject)).getLastCommittedDocument(currentDoc);
    return committedDoc == null ? getRange(manager) : new TextRange(0, committedDoc.getTextLength());
  }

  @Override
  public String toString() {
    return "file{" + myVirtualFile + ", " + myLanguageId + "}";
  }
}
