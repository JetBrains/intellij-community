/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

public class AnchorElementInfoFactory implements SmartPointerElementInfoFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.AnchorElementInfoFactory");

  @Nullable
  public SmartPointerElementInfo createElementInfo(final PsiElement element) {
    PsiElement anchor = getAnchor(element);
    if (anchor != null) {
      return new AnchorElementInfo(anchor);
    }
    return null;
  }

  @Nullable
  private static PsiElement getAnchor(PsiElement element) {
    LOG.assertTrue(element.isValid());
    PsiElement anchor = null;
    if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        anchor = ((PsiAnonymousClass)element).getBaseClassReference().getReferenceNameElement();
      }
      else {
        anchor = ((PsiClass)element).getNameIdentifier();
      }
    }
    else if (element instanceof PsiMethod) {
      anchor = ((PsiMethod)element).getNameIdentifier();
    }
    else if (element instanceof PsiVariable) {
      anchor = ((PsiVariable)element).getNameIdentifier();
    }
    else if (element instanceof XmlTag) {
      anchor = XmlTagUtil.getStartTagNameElement((XmlTag)element);
    }
    if (anchor != null && !anchor.isPhysical()) return null;
    return anchor;
  }

  private static class AnchorElementInfo implements SmartPointerElementInfo {
    protected final PsiFile myFile;
    private final RangeMarker myMarker;
    private int mySyncStartOffset;
    private int mySyncEndOffset;
    private boolean mySyncMarkerIsValid;

    private AnchorElementInfo(PsiElement anchor) {
      LOG.assertTrue(anchor.isPhysical());
      LOG.assertTrue(anchor.isValid());
      myFile = anchor.getContainingFile();
      TextRange range = anchor.getTextRange();

      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myFile.getProject());
      Document document = documentManager.getDocument(myFile);
      LOG.assertTrue(!documentManager.isUncommited(document));
      if (myFile.getTextLength() != document.getTextLength()) {
        final String docText = document.getText();
        myFile.accept(new PsiRecursiveElementWalkingVisitor() {
          @Override
          public void visitElement(PsiElement element) {
            super.visitElement(element);
            TextRange elementRange = element.getTextRange();
            final String rangeText = docText.length() <= elementRange.getEndOffset() ? "(IOOBE: "+docText.length()+" is out of "+elementRange+")" : elementRange.substring(docText);
            final String elemText = element.getText();
            if (!rangeText.equals(elemText)) {
              throw new AssertionError("PSI text doesn't equal to the document's one: element" + element + "\ndocText=" + rangeText + "\npsiText" + elemText);
            }
          }
        });
        LOG.assertTrue(false, "File=" + myFile);
      }
      myMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);

      mySyncStartOffset = range.getStartOffset();
      mySyncEndOffset = range.getEndOffset();
      mySyncMarkerIsValid = true;
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

    @Nullable
    public PsiElement restoreElement() {
      if (!mySyncMarkerIsValid) return null;
      if (!myFile.isValid()) return null;

      PsiElement anchor = myFile.findElementAt(mySyncStartOffset);
      if (anchor == null) return null;

      TextRange range = anchor.getTextRange();
      if (range.getStartOffset() != mySyncStartOffset || range.getEndOffset() != mySyncEndOffset) return null;

      if (anchor instanceof PsiIdentifier) {
        PsiElement parent = anchor.getParent();
        if (parent instanceof PsiJavaCodeReferenceElement) { // anonymous class, type
          parent = parent.getParent();
        }

        if (!anchor.equals(getAnchor(parent))) return null;

        return parent;
      }
      else if (anchor instanceof XmlToken) {
        XmlToken token = (XmlToken)anchor;

        return token.getTokenType() == XmlTokenType.XML_NAME ? token.getParent() : null;
      }
      else {
        return null;
      }
    }
  }
}
