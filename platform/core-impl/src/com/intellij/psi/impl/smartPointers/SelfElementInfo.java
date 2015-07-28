/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.editor.impl.ManualRangeMarker;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
* User: cdr
*/
public class SelfElementInfo implements SmartPointerElementInfo {
  private final VirtualFile myVirtualFile;
  private final Class myType;
  private final Project myProject;
  private final Language myLanguage;
  @Nullable private ManualRangeMarker myRangeMarker;
  @Nullable private ProperTextRange myPsiRange;

  SelfElementInfo(@NotNull Project project,
                  @NotNull ProperTextRange range,
                  @NotNull Class anchorClass,
                  @NotNull PsiFile containingFile,
                  @NotNull Language language) {
    myLanguage = language;
    myVirtualFile = PsiUtilCore.getVirtualFile(containingFile);
    myType = anchorClass;
    assert !PsiFile.class.isAssignableFrom(anchorClass) : "FileElementInfo must be used for files";

    myProject = project;
    myPsiRange = range;

    Document document = getDocumentManager().getCachedDocument(containingFile);
    if (document != null) {
      setRange(range, document);
    }
  }

  void setRange(@NotNull TextRange range, @NotNull Document document) {
    myPsiRange = null;

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      for (SmartPsiElementPointerImpl pointer : ((SmartPointerManagerImpl)SmartPointerManager.getInstance(myProject)).getAlivePointers(file)) {
        SmartPointerElementInfo info = pointer.getElementInfo();
        if (info instanceof SelfElementInfo) {
          ManualRangeMarker existing = ((SelfElementInfo)info).myRangeMarker;
          if (existing != null && range.equals(existing.getRange())) {
            myRangeMarker = existing;
            return;
          }
        }
      }
    }

    myRangeMarker = new ManualRangeMarker(getDocumentManager().getLastCommittedDocument(document), ProperTextRange.create(range), false, false, true);
  }

  private PsiDocumentManagerBase getDocumentManager() {
    return (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
  }

  @Override
  public Document getDocumentToSynchronize() {
    return myVirtualFile == null ? null : FileDocumentManager.getInstance().getCachedDocument(myVirtualFile);
  }

  // before change
  @Override
  public void fastenBelt(int offset, @Nullable RangeMarker[] cachedRangeMarkers) {
    if (myRangeMarker != null) return; // already tracks changes
    if (myPsiRange == null) return; // invalid

    Document document = myVirtualFile == null ? null : FileDocumentManager.getInstance().getDocument(myVirtualFile);
    if (document == null || !getDocumentManager().isCommitted(document)) {
      // we only have PSI range and now they say the document is uncommitted, so this PSI range is useless
      // so, just invalidate
      myPsiRange = null;
      return;
    }

    setRange(myPsiRange, document);
  }

  // after change
  @Override
  public void unfastenBelt(int offset) {
  }

  @Override
  public PsiElement restoreElement() {
    Segment segment = getPsiRange();
    if (segment == null) return null;

    PsiFile file = restoreFile();
    if (file == null || !file.isValid()) return null;

    return findElementInside(file, segment.getStartOffset(), segment.getEndOffset(), myType, myLanguage);
  }

  @Nullable
  protected Segment getPsiRange() {
    return myRangeMarker != null ? myRangeMarker.getRange() : myPsiRange;
  }

  @Override
  public PsiFile restoreFile() {
    return restoreFileFromVirtual(myVirtualFile, myProject, myLanguage);
  }

  static PsiElement findElementInside(@NotNull PsiFile file,
                                      int syncStartOffset,
                                      int syncEndOffset,
                                      @NotNull Class type,
                                      @NotNull Language language) {
    PsiElement anchor = file.getViewProvider().findElementAt(syncStartOffset, language);
    if (anchor == null) return null;

    TextRange range = anchor.getTextRange();

    if (range.getStartOffset() != syncStartOffset) return null;
    while (range.getEndOffset() < syncEndOffset) {
      anchor = anchor.getParent();
      if (anchor == null || anchor.getTextRange() == null) break;
      range = anchor.getTextRange();
    }

    while (range.getEndOffset() == syncEndOffset && anchor != null && !type.equals(anchor.getClass())) {
      anchor = anchor.getParent();
      if (anchor == null || anchor.getTextRange() == null) break;
      range = anchor.getTextRange();
    }

    return range.getEndOffset() == syncEndOffset ? anchor : null;
  }

  @Override
  public void cleanup() {
    myRangeMarker = null;
    myPsiRange = null;
  }

  public void updateRange(@NotNull DocumentEvent event, @NotNull Set<ManualRangeMarker> processedMarkers) {
    assert myPsiRange == null;
    if (myRangeMarker != null) {
      if (processedMarkers.add(myRangeMarker)) {
        myRangeMarker.applyEvent(event);
      }
      if (!myRangeMarker.isValid()) {
        myRangeMarker = null;
      }
    }
  }

  @Nullable
  public static PsiFile restoreFileFromVirtual(final VirtualFile virtualFile, @NotNull final Project project, @Nullable final Language language) {
    if (virtualFile == null) return null;

    return ApplicationManager.getApplication().runReadAction(new NullableComputable<PsiFile>() {
      @Override
      public PsiFile compute() {
        if (project.isDisposed()) return null;
        VirtualFile child;
        if (virtualFile.isValid()) {
          child = virtualFile;
        }
        else {
          VirtualFile vParent = virtualFile.getParent();
          if (vParent == null || !vParent.isDirectory()) return null;
          String name = virtualFile.getName();
          child = vParent.findChild(name);
        }
        if (child == null || !child.isValid()) return null;
        PsiFile file = PsiManager.getInstance(project).findFile(child);
        if (file != null && language != null) {
          return file.getViewProvider().getPsi(language);
        }

        return file;
      }
    });
  }

  @Nullable
  public static PsiDirectory restoreDirectoryFromVirtual(final VirtualFile virtualFile, @NotNull final Project project) {
    if (virtualFile == null) return null;

    return ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
      @Override
      public PsiDirectory compute() {
        VirtualFile child;
        if (virtualFile.isValid()) {
          child = virtualFile;
        }
        else {
          VirtualFile vParent = virtualFile.getParent();
          if (vParent == null || !vParent.isDirectory()) return null;
          String name = virtualFile.getName();
          child = vParent.findChild(name);
        }
        if (child == null || !child.isValid()) return null;
        PsiDirectory file = PsiManager.getInstance(project).findDirectory(child);
        if (file == null || !file.isValid()) return null;
        return file;
      }
    });
  }

  @Override
  public int elementHashCode() {
    VirtualFile virtualFile = myVirtualFile;
    return virtualFile == null ? 0 : virtualFile.hashCode();
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull final SmartPointerElementInfo other) {
    if (other instanceof SelfElementInfo) {
      SelfElementInfo otherInfo = (SelfElementInfo)other;
      Segment range1 = getPsiRange();
      Segment range2 = otherInfo.getPsiRange();
      return Comparing.equal(myVirtualFile, otherInfo.myVirtualFile)
             && myType == otherInfo.myType
             && range1 != null
             && range2 != null
             && range1.getStartOffset() == range2.getStartOffset()
             && range1.getEndOffset() == range2.getEndOffset()
        ;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return Comparing.equal(restoreElement(), other.restoreElement());
      }
    });
  }

  @Override
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  @Nullable
  public Segment getRange() {
    if (myRangeMarker != null) {
      Document document = getDocumentToSynchronize();
      if (document != null) {
        FrozenDocument frozen = getDocumentManager().getLastCommittedDocument(document);
        ManualRangeMarker marker = myRangeMarker;
        for (DocumentEvent event : getDocumentManager().getEventsSinceCommit(document)) {
          frozen = frozen.applyEvent(event, 0);
          marker = marker.getUpdatedRange(withFrozen(frozen, event));
          if (marker == null) return null;
        }
        return marker.getRange();
      }
      return myRangeMarker.getRange();
    }
    return myPsiRange;
  }

  @NotNull
  static DocumentEventImpl withFrozen(FrozenDocument frozen, DocumentEvent e) {
    return new DocumentEventImpl(frozen, e.getOffset(), e.getOldFragment(), e.getNewFragment(), e.getOldTimeStamp(), e.isWholeTextReplaced());
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }
}
