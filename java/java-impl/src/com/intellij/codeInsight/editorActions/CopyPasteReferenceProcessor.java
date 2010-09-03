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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Comparator;

public class CopyPasteReferenceProcessor implements CopyPastePostProcessor<ReferenceTransferableData> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.CopyPasteReferenceProcessor");
  
  public ReferenceTransferableData collectTransferableData(PsiFile file, final Editor editor, final int[] startOffsets, final int[] endOffsets) {
    if (file instanceof PsiCompiledElement) {
      file = (PsiFile) ((PsiCompiledElement) file).getMirror();
    }
    if (!(file instanceof PsiClassOwner)) {
      return new ReferenceTransferableData(new ReferenceTransferableData.ReferenceData[0]);
    }

    final ArrayList<ReferenceTransferableData.ReferenceData> array = new ArrayList<ReferenceTransferableData.ReferenceData>();
    for (int j = 0; j < startOffsets.length; j++) {
      final int startOffset = startOffsets[j];
      final int endOffset = endOffsets[j];
      final List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
      for (final PsiElement element : elements) {
        if (element instanceof PsiJavaCodeReferenceElement) {
          if (!((PsiJavaCodeReferenceElement)element).isQualified()) {
            final JavaResolveResult resolveResult = ((PsiJavaCodeReferenceElement)element).advancedResolve(false);
            final PsiElement refElement = resolveResult.getElement();
            if (refElement != null && refElement.getContainingFile() != file) {

              if (refElement instanceof PsiClass) {
                if (refElement.getContainingFile() != element.getContainingFile()) {
                  final String qName = ((PsiClass)refElement).getQualifiedName();
                  if (qName != null) {
                    addReferenceData(element, array, startOffset, qName, null);
                  }
                }
              }
              else if (resolveResult.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
                final String classQName = ((PsiMember)refElement).getContainingClass().getQualifiedName();
                final String name = ((PsiNamedElement)refElement).getName();
                if (classQName != null && name != null) {
                  addReferenceData(element, array, startOffset, classQName, name);
                }
              }
            }
          }
        }
      }
    }
    return new ReferenceTransferableData(array.toArray(new ReferenceTransferableData.ReferenceData[array.size()]));
  }

  @Nullable
  public ReferenceTransferableData extractTransferableData(final Transferable content) {
    ReferenceTransferableData referenceData = null;
    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE != CodeInsightSettings.NO) {
      try {
        final DataFlavor flavor = ReferenceTransferableData.ReferenceData.getDataFlavor();
        if (flavor != null) {
          referenceData = (ReferenceTransferableData)content.getTransferData(flavor);
        }
      }
      catch (UnsupportedFlavorException ignored) {
      }
      catch (IOException ignored) {
      }
    }

    if (referenceData != null) { // copy to prevent changing of original by convertLineSeparators
      return referenceData.clone();
    }

    return null;
  }

  public void processTransferableData(final Project project, final Editor editor, final RangeMarker bounds, final ReferenceTransferableData value) {
    if (DumbService.getInstance(project).isDumb()) {
      return;
    }
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (!(file instanceof PsiClassOwner)) {
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final ReferenceTransferableData.ReferenceData[] referenceData = value.getData();
    final PsiJavaCodeReferenceElement[] refs = findReferencesToRestore(file, bounds, referenceData);
    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
      askReferencesToRestore(project, refs, referenceData);
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        restoreReferences(referenceData, refs);
      }
    });
  }

  private static void addReferenceData(final PsiElement element,
                                final ArrayList<ReferenceTransferableData.ReferenceData> array,
                                final int startOffset,
                                final String qClassName, final String staticMemberName) {
    final TextRange range = element.getTextRange();
    array.add(
        new ReferenceTransferableData.ReferenceData(
            range.getStartOffset() - startOffset,
            range.getEndOffset() - startOffset,
            qClassName, staticMemberName));
  }

  private static PsiJavaCodeReferenceElement[] findReferencesToRestore(PsiFile file,
                                                                       RangeMarker bounds,
                                                                       ReferenceTransferableData.ReferenceData[] referenceData) {
    PsiManager manager = file.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    PsiResolveHelper helper = facade.getResolveHelper();
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[referenceData.length];
    for (int i = 0; i < referenceData.length; i++) {
      ReferenceTransferableData.ReferenceData data = referenceData[i];

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
              PsiElement referent = reference.resolve();
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

  private static void restoreReferences(ReferenceTransferableData.ReferenceData[] referenceData,
                                        PsiJavaCodeReferenceElement[] refs) {
    for (int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement reference = refs[i];
      if (reference == null) continue;
      try {
        PsiManager manager = reference.getManager();
        ReferenceTransferableData.ReferenceData refData = referenceData[i];
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

  private static void askReferencesToRestore(Project project, PsiJavaCodeReferenceElement[] refs,
                                      ReferenceTransferableData.ReferenceData[] referenceData) {
    PsiManager manager = PsiManager.getInstance(project);

    ArrayList<Object> array = new ArrayList<Object>();
    Object[] refObjects = new Object[refs.length];
    for (int i = 0; i < referenceData.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      if (ref != null) {
        LOG.assertTrue(ref.isValid());
        ReferenceTransferableData.ReferenceData data = referenceData[i];
        PsiClass refClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(data.qClassName, ref.getResolveScope());
        if (refClass == null) continue;

        Object refObject = refClass;
        if (data.staticMemberName != null) {
          //Show static members as Strings
          refObject = refClass.getQualifiedName() + "." + data.staticMemberName;
        }
        refObjects[i] = refObject;

        if (!array.contains(refObject)) {
          array.add(refObject);
        }
      }
    }
    if (array.isEmpty()) return;

    Object[] selectedObjects = ArrayUtil.toObjectArray(array);
    Arrays.sort(
      selectedObjects,
      new Comparator<Object>() {
        public int compare(Object o1, Object o2) {
          String fqName1 = getFQName(o1);
          String fqName2 = getFQName(o2);
          return fqName1.compareToIgnoreCase(fqName2);
        }
      }
    );

    RestoreReferencesDialog dialog = new RestoreReferencesDialog(project, selectedObjects);
    dialog.show();
    selectedObjects = dialog.getSelectedElements();

    for (int i = 0; i < referenceData.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      if (ref != null) {
        LOG.assertTrue(ref.isValid());
        Object refObject = refObjects[i];
        boolean found = false;
        for (Object selected : selectedObjects) {
          if (refObject.equals(selected)) {
            found = true;
            break;
          }
        }
        if (!found) {
          refs[i] = null;
        }
      }
    }
  }

  private static String getFQName(Object element) {
    return element instanceof PsiClass ? ((PsiClass)element).getQualifiedName() : (String)element;
  }
}
