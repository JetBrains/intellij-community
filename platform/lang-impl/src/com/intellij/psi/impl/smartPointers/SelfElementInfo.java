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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

/**
* User: cdr
*/
public class SelfElementInfo implements SmartPointerElementInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SelfElementInfo");
  protected final VirtualFile myVirtualFile;
  private Reference<RangeMarker> myMarkerRef; // create marker only in case of live document
  private int mySyncStartOffset;
  private int mySyncEndOffset;
  protected boolean mySyncMarkerIsValid;
  private Class myType;
  protected final Project myProject;
  private RangeMarker myRangeMarker; //maintain hard reference during modification

  public SelfElementInfo(@NotNull PsiElement anchor, PsiFile containingFile) {
    LOG.assertTrue(anchor.isPhysical());
    LOG.assertTrue(anchor.isValid());
    PsiFile file = anchor.getContainingFile();
    myVirtualFile = file.getVirtualFile();
    TextRange range = anchor.getTextRange();
    LOG.assertTrue(range != null, anchor);
    range = getPersistentAnchorRange(anchor);

    myProject = file.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(file);
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

    if (documentManager.isUncommited(document)) {
      mySyncMarkerIsValid = false;
    }
    else {
      mySyncMarkerIsValid = true;
      myType = anchor.getClass();
    }
    mySyncStartOffset = range.getStartOffset();
    mySyncEndOffset = range.getEndOffset();
  }

  protected TextRange getPersistentAnchorRange(final PsiElement anchor) {
    return anchor.getTextRange();
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
        myRangeMarker = marker; //make sure marker wont be gced
      //}
    }
    else if (!marker.isValid()) {
      mySyncMarkerIsValid = false;
      marker.dispose();
      setMarker(null);
    }
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
        assert mySyncEndOffset <= marker.getDocument().getTextLength();
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
    PsiFile file = restoreFileFromVirtual(myVirtualFile, myProject);
    if (file == null || !file.isValid()) return null;

    final int syncStartOffset = getSyncStartOffset();
    final int syncEndOffset = getSyncEndOffset();

    PsiElement anchor = file.getViewProvider().findElementAt(syncStartOffset, file.getLanguage());
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
  public static PsiFile restoreFile(PsiFile file,@NotNull Project project) {
    if (file == null) return null;
    if (file.isValid()) return file;
    VirtualFile virtualFile = file.getVirtualFile();
    return restoreFileFromVirtual(virtualFile, project);
  }

  @Nullable
  public static PsiFile restoreFileFromVirtual(VirtualFile virtualFile, @NotNull Project project) {
    if (virtualFile == null) return null;

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
  public boolean pointsToTheSameElementAs(SmartPointerElementInfo other) {
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
  public Segment getSegment() {
    if (!mySyncMarkerIsValid) return null;
    return new TextRange(getSyncStartOffset(), getSyncEndOffset());
  }
}
