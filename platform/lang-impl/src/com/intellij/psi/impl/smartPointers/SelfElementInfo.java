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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* User: cdr
*/
class SelfElementInfo implements SmartPointerElementInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SelfElementInfo");
  protected PsiFile myFile;
  private final RangeMarker myMarker;
  private int mySyncStartOffset;
  private int mySyncEndOffset;
  private boolean mySyncMarkerIsValid;
  private Class myType;
  private final Project myProject;

  public SelfElementInfo(@NotNull PsiElement anchor, @NotNull Document document) {
    LOG.assertTrue(anchor.isPhysical());
    myFile = anchor.getContainingFile();
    TextRange range = anchor.getTextRange();
    LOG.assertTrue(range != null, anchor);

    myProject = myFile.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);

    if (documentManager.isUncommited(document)) {
      mySyncMarkerIsValid = false;
      myMarker = document.createRangeMarker(0, 0, false);
    }
    else {
      myMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);
      range = getPersistentAnchorRange(anchor, document);

      mySyncStartOffset = range.getStartOffset();
      mySyncEndOffset = range.getEndOffset();
      mySyncMarkerIsValid = true;
      myType = anchor.getClass();
    }
  }

  protected TextRange getPersistentAnchorRange(final PsiElement anchor, final Document document) {
    return anchor.getTextRange();
  }

  public Document getDocumentToSynchronize() {
    return myMarker.getDocument();
  }

  public void documentAndPsiInSync() {
    if (!myMarker.isValid()) {
      mySyncMarkerIsValid = false;
      return;
    }

    mySyncStartOffset = myMarker.getStartOffset();
    mySyncEndOffset = myMarker.getEndOffset();
  }

  public PsiElement restoreElement() {
    if (!mySyncMarkerIsValid) return null;
    myFile = restoreFile(myFile, myProject);
    if (myFile == null) return null;

    final int syncStartOffset = getSyncStartOffset();
    final int syncEndOffset = getSyncEndOffset();

    PsiElement anchor = myFile.getViewProvider().findElementAt(syncStartOffset, myFile.getLanguage());
    if (anchor == null) return null;

    TextRange range = anchor.getTextRange();

    if (range.getStartOffset() != syncStartOffset) return null;
    while (range.getEndOffset() < syncEndOffset) {
      anchor = anchor.getParent();
      if (anchor == null || anchor.getTextRange() == null) break;
      range = anchor.getTextRange();
    }

    while (range.getEndOffset() == syncEndOffset && anchor != null && !myType.equals(anchor.getClass())) {
      anchor = anchor.getParent();
      if (anchor == null || anchor.getTextRange() == null) break;
      range = anchor.getTextRange();
    }

    if (range.getEndOffset() == syncEndOffset) return anchor;
    return null;
  }

  @Nullable
  public static PsiFile restoreFile(PsiFile file,@NotNull Project project) {
    if (file == null) return null;
    if (file.isValid()) return file;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    VirtualFile vParent = virtualFile.getParent();
    if (vParent == null || !vParent.isDirectory()) return null;
    String name = file.getName();
    VirtualFile child = vParent.findChild(name);
    if (child == null || !child.isValid()) return null;
    file = PsiManager.getInstance(project).findFile(child);
    if (file == null || !file.isValid()) return null;
    return file;
  }

  protected int getSyncEndOffset() {
    return mySyncEndOffset;
  }

  protected int getSyncStartOffset() {
    return mySyncStartOffset;
  }
}
