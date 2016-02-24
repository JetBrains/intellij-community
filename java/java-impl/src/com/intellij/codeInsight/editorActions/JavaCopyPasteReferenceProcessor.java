/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

/**
 * @author peter
 */
public class JavaCopyPasteReferenceProcessor extends CopyPasteReferenceProcessor<PsiJavaCodeReferenceElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.JavaCopyPasteReferenceProcessor");

  @Override
  protected void addReferenceData(PsiFile file, int startOffset, PsiElement element, ArrayList<ReferenceData> to) {
    if (element instanceof PsiJavaCodeReferenceElement) {
      if (!((PsiJavaCodeReferenceElement)element).isQualified()) {
        final JavaResolveResult resolveResult = ((PsiJavaCodeReferenceElement)element).advancedResolve(false);
        final PsiElement refElement = resolveResult.getElement();
        if (refElement != null) {

          if (refElement instanceof PsiClass) {
            final String qName = ((PsiClass)refElement).getQualifiedName();
            if (qName != null) {
              addReferenceData(element, to, startOffset, qName, null);
            }
          }
          else if (resolveResult.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
            final String classQName = ((PsiMember)refElement).getContainingClass().getQualifiedName();
            final String name = ((PsiNamedElement)refElement).getName();
            if (classQName != null && name != null) {
              addReferenceData(element, to, startOffset, classQName, name);
            }
          }
        }
      }
    }
  }


  @Override
  protected PsiJavaCodeReferenceElement[] findReferencesToRestore(PsiFile file,
                                                                       RangeMarker bounds,
                                                                       ReferenceData[] referenceData) {
    PsiManager manager = file.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    PsiResolveHelper helper = facade.getResolveHelper();
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[referenceData.length];
    for (int i = 0; i < referenceData.length; i++) {
      ReferenceData data = referenceData[i];

      PsiClass refClass = facade.findClass(data.qClassName, file.getResolveScope());
      if (refClass == null) continue;

      int startOffset = data.startOffset + bounds.getStartOffset();
      int endOffset = data.endOffset + bounds.getStartOffset();
      PsiElement element = file.findElementAt(startOffset);

      if (element instanceof PsiIdentifier && element.getParent() instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)element.getParent();
        TextRange range = reference.getTextRange();
        if (range.getStartOffset() == startOffset && range.getEndOffset() == endOffset) {
          if (data.staticMemberName == null) {
            PsiClass refClass1 = helper.resolveReferencedClass(reference.getText(), reference);
            if (refClass1 == null || !manager.areElementsEquivalent(refClass, refClass1)) {
              refs[i] = reference;
            }
          }
          else {
            if (reference instanceof PsiReferenceExpression) {
              PsiElement referent = resolveReferenceIgnoreOverriding(reference);

              if (!(referent instanceof PsiNamedElement)
                  || !data.staticMemberName.equals(((PsiNamedElement)referent).getName())
                  || !(referent instanceof PsiMember)
                  || ((PsiMember)referent).getContainingClass() == null
                  || !data.qClassName.equals(((PsiMember)referent).getContainingClass().getQualifiedName())) {
                refs[i] = reference;
              }
            }
          }
        }
      }
    }
    return refs;
  }

  @Override
  protected void restoreReferences(ReferenceData[] referenceData,
                                        PsiJavaCodeReferenceElement[] refs) {
    for (int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement reference = refs[i];
      if (reference == null || !reference.isValid()) continue;
      try {
        PsiManager manager = reference.getManager();
        ReferenceData refData = referenceData[i];
        PsiClass refClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(refData.qClassName, reference.getResolveScope());
        if (refClass != null) {
          if (refData.staticMemberName == null) {
            reference.bindToElement(refClass);
          }
          else {
            LOG.assertTrue(reference instanceof PsiReferenceExpression);
            ((PsiReferenceExpression)reference).bindToElementViaStaticImport(refClass);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
