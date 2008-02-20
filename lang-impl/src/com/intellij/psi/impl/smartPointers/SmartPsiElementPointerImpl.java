package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import org.jetbrains.annotations.Nullable;

class SmartPsiElementPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.psi.impl.smartPointers.SmartPsiElementPointerImpl");

  private E myElement;
  private SmartPointerElementInfo myElementInfo;
  private final Project myProject;

  public SmartPsiElementPointerImpl(Project project, E element) {
    myProject = project;
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myElement = element;
    myElementInfo = null;

    // Assert document committed.
    PsiFile file = element.getContainingFile();
    if (file != null) {
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
      if (psiDocumentManager instanceof PsiDocumentManagerImpl) {
        Document doc = psiDocumentManager.getCachedDocument(file);
        if (doc != null) {
          //[ven] this is a really NASTY hack; when no smart pointer is kept on UsageInfo then remove this conditional
          if (!(element instanceof PsiFile)) {
            if (psiDocumentManager.isUncommited(doc) &&
                             !((PsiDocumentManagerImpl)psiDocumentManager).isCommittingDocument(doc)) {
              LOG.assertTrue(false, element);
            }
          }
        }
      }
    }
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof SmartPsiElementPointer)) return false;
    SmartPsiElementPointer pointer = (SmartPsiElementPointer)obj;
    return Comparing.equal(pointer.getElement(), getElement());
  }

  public int hashCode() {
    PsiElement element = getElement();
    return element != null ? element.hashCode() : 0;
  }

  @Nullable
  public E getElement() {
    if (myElement != null && !myElement.isValid()) {
      if (myElementInfo == null) {
        myElement = null;
      }
      else {
        PsiElement restored = myElementInfo.restoreElement();
        if (restored != null && (!areElementKindEqual(restored, myElement) || !restored.isValid())) {
          restored = null;
        }

        myElement = (E) restored;
      }
    }

    if (myElementInfo != null && myElement != null) {
      Document document = myElementInfo.getDocumentToSynchronize();
      if (document != null && PsiDocumentManager.getInstance(myProject).isUncommited(document)) return myElement; // keep element info if document is modified
    }
    // myElementInfo = null;

    return myElement;
  }

  @Nullable
  private SmartPointerElementInfo createElementInfo() {
    if (myElement instanceof PsiCompiledElement) return null;

    if (myElement.getContainingFile() == null) return null;
    if (!myElement.isPhysical()) return null;

    for(SmartPointerElementInfoFactory factory: Extensions.getExtensions(SmartPointerElementInfoFactory.EP_NAME)) {
      final SmartPointerElementInfo result = factory.createElementInfo(myElement);
      if (result != null) {
        return result;
      }
    }

    return new SelfElementInfo(myElement);
  }

  private static boolean areElementKindEqual(PsiElement element1, PsiElement element2) {
    return element1.getClass().equals(element2.getClass()); //?
  }

  public void documentAndPsiInSync() {
    if (myElementInfo != null) {
      myElementInfo.documentAndPsiInSync();
    }
  }

  public void fastenBelt() {
    if (myElementInfo != null && myElement != null && myElement.isValid()) return;

    if (myElementInfo == null && myElement != null && myElement.isValid()) {
      myElementInfo = createElementInfo();
    }
  }

  private static class SelfElementInfo implements SmartPointerElementInfo {
    protected final PsiFile myFile;
    private final RangeMarker myMarker;
    private int mySyncStartOffset;
    private int mySyncEndOffset;
    private boolean mySyncMarkerIsValid;
    private Class myType;

    public SelfElementInfo(PsiElement anchor) {
      LOG.assertTrue(anchor.isPhysical());
      myFile = anchor.getContainingFile();
      TextRange range = anchor.getTextRange();

      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myFile.getProject());
      Document document = documentManager.getDocument(myFile);

      // LOG.assertTrue(!documentManager.isUncommited(document));

      if (documentManager.isUncommited(document)) {
        mySyncMarkerIsValid = false;
        myMarker = document.createRangeMarker(0, 0, false);
      }
      else {
        myMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);

        mySyncStartOffset = range.getStartOffset();
        mySyncEndOffset = range.getEndOffset();
        mySyncMarkerIsValid = true;
        myType = anchor.getClass();
      }
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
      if (!myFile.isValid()) return null;

      PsiElement anchor = myFile.getViewProvider().findElementAt(mySyncStartOffset, myFile.getLanguage());
      if (anchor == null) return null;

      TextRange range = anchor.getTextRange();

      if (range.getStartOffset() != mySyncStartOffset) return null;
      while (range.getEndOffset() < mySyncEndOffset) {
        anchor = anchor.getParent();
        if (anchor == null || anchor.getTextRange() == null) break;
        range = anchor.getTextRange();
      }

      while (range.getEndOffset() == mySyncEndOffset && anchor != null && !myType.equals(anchor.getClass())) {
        anchor = anchor.getParent();
        if (anchor == null || anchor.getTextRange() == null) break;
        range = anchor.getTextRange();
      }

      if (range.getEndOffset() == mySyncEndOffset) return anchor;
      return null;
    }
  }
}
