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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

/**
* User: cdr
*/
public class SelfElementInfo implements SmartPointerElementInfo {
  private final VirtualFile myVirtualFile;
  private Reference<RangeMarker> myMarkerRef; // create marker only in case of live document
  private volatile int mySyncStartOffset;
  private volatile int mySyncEndOffset;
  volatile boolean mySyncMarkerIsValid;
  private final Class myType;
  private final Project myProject;
  @SuppressWarnings("UnusedDeclaration")
  private volatile RangeMarker myRangeMarker; //maintains hard reference during modification
  private final Language myLanguage;

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
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getCachedDocument(containingFile);
    if (document != null && documentManager.isUncommited(document)) {
      mySyncMarkerIsValid = false;
    }
    else {
      mySyncMarkerIsValid = true;
      setRange(range);
    }
  }

  void setRange(@NotNull Segment range) {
    mySyncStartOffset = range.getStartOffset();
    mySyncEndOffset = range.getEndOffset();
  }

  @Override
  public Document getDocumentToSynchronize() {
    RangeMarker marker = getMarker();
    if (marker != null) {
      return marker.getDocument();
    }
    return myVirtualFile == null ? null : FileDocumentManager.getInstance().getCachedDocument(myVirtualFile);
  }

  // before change
  @Override
  public void fastenBelt(int offset, @Nullable RangeMarker[] cachedRangeMarkers) {
    if (!mySyncMarkerIsValid) {
      return;
    }
    RangeMarker marker = getMarker();
    int actualEndOffset = marker == null || !marker.isValid() ? getSyncEndOffset() : marker.getEndOffset();
    if (offset > actualEndOffset) {
      return; // no need to update, the change is far after
    }
    if (marker == null) {
      Document document = myVirtualFile == null ? null : FileDocumentManager.getInstance().getDocument(myVirtualFile);
      if (document == null) {
        mySyncMarkerIsValid = false;
      }
      else {
        int start = Math.min(getSyncStartOffset(), document.getTextLength());
        int end = Math.min(Math.max(getSyncEndOffset(), start), document.getTextLength());
        // use supplied cached markers if available
        if (cachedRangeMarkers != null) {
          for (RangeMarker cachedRangeMarker : cachedRangeMarkers) {
            if (cachedRangeMarker.isValid() &&
                cachedRangeMarker.getStartOffset() == start &&
                cachedRangeMarker.getEndOffset() == end) {
              marker = cachedRangeMarker;
              break;
            }
          }
        }
        if (marker == null) {
          marker = document.createRangeMarker(start, end, true);
        }
      }
      setMarker(marker);
    }
    else if (marker.isValid()) {
      setRange(marker);
    }
    else {
      mySyncMarkerIsValid = false;
      setMarker(null);
      marker = null;
    }
    myRangeMarker = marker; //make sure marker wont be gced
  }

  // after change
  @Override
  public void unfastenBelt(int offset) {
    if (!mySyncMarkerIsValid) {
      return;
    }
    RangeMarker marker = getMarker();
    if (marker != null) {
      if (marker.isValid()) {
        setRange(marker);
        assert mySyncEndOffset <= marker.getDocument().getTextLength() : "mySyncEndOffset: "+mySyncEndOffset+"; docLength: "+marker.getDocument().getTextLength()+"; marker: "+marker +"; "+marker.getClass();
      }
      else {
        mySyncMarkerIsValid = false;
      }
    }
    myRangeMarker = null;  // clear hard ref to avoid leak, but hold soft ref (in myMarkerRef) for not recreating marker too often
  }

  @Override
  public PsiElement restoreElement() {
    if (!mySyncMarkerIsValid) return null;
    PsiFile file = restoreFile();
    if (file == null || !file.isValid()) return null;

    return restoreFromFile(file);
  }

  private PsiElement restoreFromFile(@NotNull PsiFile file) {
    final int syncStartOffset = getSyncStartOffset();
    final int syncEndOffset = getSyncEndOffset();

    return findElementInside(file, syncStartOffset, syncEndOffset, myType, myLanguage);
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

  private RangeMarker getMarker() {
    return com.intellij.reference.SoftReference.dereference(myMarkerRef);
  }

  @Override
  public void cleanup() {
    RangeMarker marker = getMarker();
    if (marker != null) marker.dispose();
    unfastenBelt(0);
    setMarker(null);
    mySyncMarkerIsValid = false;
  }

  private void setMarker(RangeMarker marker) {
    myMarkerRef = marker == null ? null : new SoftReference<RangeMarker>(marker);
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

  int getSyncEndOffset() {
    RangeMarker marker = myRangeMarker;
    return marker == null || !marker.isValid() ? mySyncEndOffset : marker.getEndOffset();
  }

  int getSyncStartOffset() {
    RangeMarker marker = myRangeMarker;
    return marker == null || !marker.isValid() ? mySyncStartOffset : marker.getStartOffset();
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
      return Comparing.equal(myVirtualFile, otherInfo.myVirtualFile)
             && myType == otherInfo.myType
             && mySyncMarkerIsValid
             && otherInfo.mySyncMarkerIsValid
             && getSyncStartOffset() == otherInfo.getSyncStartOffset()
             && getSyncEndOffset() == otherInfo.getSyncEndOffset()
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
  public Segment getRange() {
    if (!mySyncMarkerIsValid) return null;
    return new TextRange(getSyncStartOffset(), getSyncEndOffset());
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }
}
