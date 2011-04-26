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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
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
  private int mySyncStartOffset;
  private int mySyncEndOffset;
  protected boolean mySyncMarkerIsValid;
  private final Class myType;
  protected final Project myProject;
  @SuppressWarnings({"UnusedDeclaration"})
  private RangeMarker myRangeMarker; //maintain hard reference during modification
  protected final Language myLanguage;

  protected SelfElementInfo(@NotNull Project project, @NotNull PsiElement anchor) {
    this(project, anchor.getTextRange(), anchor.getClass(), anchor.getContainingFile(), anchor.getContainingFile().getLanguage());
  }
  public SelfElementInfo(@NotNull Project project,
                         @NotNull TextRange anchor,
                         @NotNull Class anchorClass,
                         @NotNull PsiFile containingFile,
                         @NotNull Language language) {
    myLanguage = language;
    myVirtualFile = containingFile.getVirtualFile();
    myType = anchorClass;

    myProject = project;
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(containingFile);
    if (document == null || documentManager.isUncommited(document)) {
      mySyncMarkerIsValid = false;
      return;
    }

    //if (file.getTextLength() != document.getTextLength()) {
    //  final String docText = document.getText();
    //  file.accept(new PsiRecursiveElementWalkingVisitor() {
    //    @Override
    //    public void visitElement(PsiElement element) {
    //      super.visitElement(element);
    //      TextRange elementRange = element.getTextRange();
    //      final String rangeText = docText.length() < elementRange.getEndOffset() ? "(IOOBE: "+elementRange +" is out of (0,"+docText.length()+"))" : elementRange.substring(docText);
    //      final String elemText = element.getText();
    //      if (!rangeText.equals(elemText)) {
    //        throw new AssertionError("PSI text doesn't equal to the document's one: element: " + element + "\ndocText=" + rangeText + "\npsiText: " + elemText);
    //      }
    //    }
    //  });
    //  LOG.error("File=" + file);
    //}

    mySyncMarkerIsValid = true;
    setRange(anchor);
  }

  protected void setRange(TextRange range) {
    mySyncStartOffset = range.getStartOffset();
    mySyncEndOffset = range.getEndOffset();
  }

  public Document getDocumentToSynchronize() {
    RangeMarker marker = getMarker();
    if (marker != null) {
      return marker.getDocument();
    }
    return FileDocumentManager.getInstance().getCachedDocument(myVirtualFile);
  }

  // before change
  @Override
  public void fastenBelt(int offset) {
    if (!mySyncMarkerIsValid) return;
    RangeMarker marker = getMarker();
    int actualEndOffset = marker == null || !marker.isValid() ? getSyncEndOffset() : marker.getEndOffset();
    if (offset > actualEndOffset) {
      return; // no need to update, the change is far after
    }
    if (marker == null) {
      Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
      if (document == null) {
        mySyncMarkerIsValid = false;
        return;
      }
      //PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject)).getSynchronizer();
      //boolean inSynchronization = synchronizer.isInSynchronization(document);

      //if (!inSynchronization) { // otherwise doc offsets are incorrect
        int start = Math.min(getSyncStartOffset(), document.getTextLength());
        int end = Math.min(Math.max(getSyncEndOffset(), start), document.getTextLength());
        marker = document.createRangeMarker(start, end, true);
        setMarker(marker);
      //}
    }
    else if (!marker.isValid()) {
      mySyncMarkerIsValid = false;
      marker.dispose();
      setMarker(null);
      marker = null;
    }
    myRangeMarker = marker; //make sure marker wont be gced
  }

  // after change
  @Override
  public void unfastenBelt(int offset) {
    if (!mySyncMarkerIsValid) return;
    RangeMarker marker = getMarker();
    if (marker != null) {
      if (marker.isValid()) {
        mySyncStartOffset = marker.getStartOffset();
        mySyncEndOffset = marker.getEndOffset();
        assert mySyncEndOffset <= marker.getDocument().getTextLength() : "mySyncEndOffset: "+mySyncEndOffset+"; docLength: "+marker.getDocument().getTextLength()+"; marker: "+marker +"; "+marker.getClass();
      }
      else {
        mySyncMarkerIsValid = false;
      }
    }
    myRangeMarker = null;
  }

  // commit
  public void documentAndPsiInSync() {
  }

  public PsiElement restoreElement() {
    if (!mySyncMarkerIsValid) return null;
    PsiFile file = restoreFile();
    if (file == null || !file.isValid()) return null;

    final int syncStartOffset = getSyncStartOffset();
    final int syncEndOffset = getSyncEndOffset();

    return findElementInside(file, syncStartOffset, syncEndOffset, myType, myLanguage);
  }

  protected PsiFile restoreFile() {
    return restoreFileFromVirtual(myVirtualFile, myProject);
  }

  protected static PsiElement findElementInside(@NotNull PsiFile file, int syncStartOffset, int syncEndOffset, @NotNull Class type, @NotNull Language language) {
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

    if (range.getEndOffset() == syncEndOffset) return anchor;
    return null;
  }

  @Override
  public void dispose() {
    RangeMarker marker = getMarker();
    if (marker != null) {
      marker.dispose();
      setMarker(null);
    }
  }

  private RangeMarker getMarker() {
    Reference<RangeMarker> ref = myMarkerRef;
    return ref == null ? null : ref.get();
  }

  private void setMarker(RangeMarker marker) {
    myMarkerRef = marker == null ? null : new SoftReference<RangeMarker>(marker);
  }

  @Nullable
  public static PsiFile restoreFileFromVirtual(final VirtualFile virtualFile, @NotNull final Project project) {
    if (virtualFile == null) return null;

    return ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      public PsiFile compute() {
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
        if (file == null || !file.isValid()) return null;
        return file;
      }
    });
  }

  @Nullable
  public static PsiDirectory restoreDirectoryFromVirtual(final VirtualFile virtualFile, @NotNull final Project project) {
    if (virtualFile == null) return null;

    return ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
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

  protected int getSyncEndOffset() {
    return mySyncEndOffset;
  }

  protected int getSyncStartOffset() {
    return mySyncStartOffset;
  }

  @Override
  public int elementHashCode() {
    return myVirtualFile.hashCode();
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other) {
    if (other instanceof SelfElementInfo) {
      return myVirtualFile == ((SelfElementInfo)other).myVirtualFile
        && myType == ((SelfElementInfo)other).myType
        && mySyncStartOffset == ((SelfElementInfo)other).mySyncStartOffset
        && mySyncEndOffset == ((SelfElementInfo)other).mySyncEndOffset
        ;
    }
    return Comparing.equal(restoreElement(), other.restoreElement());
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
