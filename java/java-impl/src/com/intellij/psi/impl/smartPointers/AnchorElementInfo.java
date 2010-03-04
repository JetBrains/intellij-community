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
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* User: cdr
*/
class AnchorElementInfo implements SmartPointerElementInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.AnchorElementInfo");
  private PsiFile myFile;
  private final RangeMarker myMarker;
  private int mySyncStartOffset;
  private int mySyncEndOffset;
  private boolean mySyncMarkerIsValid;
  private final Project myProject;

  AnchorElementInfo(@NotNull PsiElement anchor) {
    LOG.assertTrue(anchor.isPhysical());
    LOG.assertTrue(anchor.isValid());
    myFile = anchor.getContainingFile();
    myProject = myFile.getProject();
    TextRange range = anchor.getTextRange();

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
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
      LOG.error("File=" + myFile);
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
    myFile = SelfElementInfo.restoreFile(myFile, myProject);
    if (myFile == null) return null;
    PsiElement anchor = myFile.findElementAt(mySyncStartOffset);
    if (anchor == null) return null;

    TextRange range = anchor.getTextRange();
    if (range.getStartOffset() != mySyncStartOffset || range.getEndOffset() != mySyncEndOffset) return null;

    if (anchor instanceof PsiIdentifier) {
      PsiElement parent = anchor.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) { // anonymous class, type
        parent = parent.getParent();
      }

      if (!anchor.equals(AnchorElementInfoFactory.getAnchor(parent))) return null;

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
