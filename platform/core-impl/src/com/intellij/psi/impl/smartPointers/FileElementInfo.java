/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
  private final VirtualFile myVirtualFile;
  private final Project myProject;
  private final Language myLanguage;
  private final Class<? extends PsiFile> myFileClass;

  public FileElementInfo(@NotNull final PsiFile file) {
    myVirtualFile = file.getVirtualFile();
    myProject = file.getProject();
    myLanguage = LanguageUtil.getRootLanguage(file);
    myFileClass = file.getClass();
  }

  @Override
  public PsiElement restoreElement() {
    PsiFile file = SelfElementInfo.restoreFileFromVirtual(myVirtualFile, myProject, myLanguage);
    return myFileClass.isInstance(file) ? file : null;
  }

  @Override
  public PsiFile restoreFile() {
    PsiElement element = restoreElement();
    return element == null ? null : element.getContainingFile(); // can be directory
  }

  @Override
  public int elementHashCode() {
    return myVirtualFile.hashCode();
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other) {
    return other instanceof FileElementInfo && Comparing.equal(myVirtualFile, ((FileElementInfo)other).myVirtualFile);
  }

  @Override
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  public Segment getRange() {
    if (!myVirtualFile.isValid()) return null;

    Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
    return document == null ? null : TextRange.from(0, document.getTextLength());
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @Nullable
  @Override
  public Segment getPsiRange() {
    Document currentDoc = FileDocumentManager.getInstance().getCachedDocument(myVirtualFile);
    Document committedDoc = currentDoc == null ? null :
                                  ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject)).getLastCommittedDocument(currentDoc);
    return committedDoc == null ? getRange() : new TextRange(0, committedDoc.getTextLength());
  }

  @Override
  public String toString() {
    return "file{" + myVirtualFile + ", " + myLanguage + "}";
  }
}
