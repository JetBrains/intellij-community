/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.*;

public abstract class CopyPasteReferenceProcessor<TRef extends PsiElement> extends CopyPastePostProcessor<ReferenceTransferableData> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.CopyPasteReferenceProcessor");
  
  @NotNull
  @Override
  public List<ReferenceTransferableData> collectTransferableData(PsiFile file, final Editor editor, final int[] startOffsets, final int[] endOffsets) {
    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO) {
      return Collections.emptyList();
    }

    if (!(file instanceof PsiClassOwner)) {
      return Collections.emptyList();
    }

    final ArrayList<ReferenceData> array = new ArrayList<>();
    for (int j = 0; j < startOffsets.length; j++) {
      final int startOffset = startOffsets[j];
      for (final PsiElement element : CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffsets[j])) {
        addReferenceData(file, startOffset, element, array);
      }
    }

    if (array.isEmpty()) {
      return Collections.emptyList();
    }
    
    return Collections.singletonList(new ReferenceTransferableData(array.toArray(new ReferenceData[array.size()])));
  }

  protected abstract void addReferenceData(PsiFile file, int startOffset, PsiElement element, ArrayList<ReferenceData> to);

  @NotNull
  @Override
  public List<ReferenceTransferableData> extractTransferableData(final Transferable content) {
    ReferenceTransferableData referenceData = null;
    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE != CodeInsightSettings.NO) {
      try {
        final DataFlavor flavor = ReferenceData.getDataFlavor();
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
      return Collections.singletonList(referenceData.clone());
    }

    return Collections.emptyList();
  }

  @Override
  public void processTransferableData(final Project project,
                                      final Editor editor,
                                      final RangeMarker bounds,
                                      int caretOffset,
                                      Ref<Boolean> indented, final List<ReferenceTransferableData> values) {
    if (DumbService.getInstance(project).isDumb()) {
      return;
    }
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (!(file instanceof PsiClassOwner)) {
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    assert values.size() == 1;
    final ReferenceData[] referenceData = values.get(0).getData();
    final TRef[] refs = findReferencesToRestore(file, bounds, referenceData);
    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
      askReferencesToRestore(project, refs, referenceData);
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    ApplicationManager.getApplication().runWriteAction(() -> restoreReferences(referenceData, refs));
  }

  protected static void addReferenceData(final PsiElement element,
                                final ArrayList<ReferenceData> array,
                                final int startOffset,
                                final String qClassName, @Nullable final String staticMemberName) {
    final TextRange range = element.getTextRange();
    array.add(
        new ReferenceData(
            range.getStartOffset() - startOffset,
            range.getEndOffset() - startOffset,
            qClassName, staticMemberName));
  }

  protected abstract TRef[] findReferencesToRestore(PsiFile file,
                                                                           RangeMarker bounds,
                                                                           ReferenceData[] referenceData);

  protected PsiElement resolveReferenceIgnoreOverriding(PsiPolyVariantReference reference) {
    PsiElement referent = reference.resolve();
    if (referent == null) {
      final ResolveResult[] results = reference.multiResolve(true);
      if (results.length > 0) {
        referent = results[0].getElement();
      }
    }
    return referent;
  }

  protected abstract void restoreReferences(ReferenceData[] referenceData,
                                            TRef[] refs);

  private static void askReferencesToRestore(Project project, PsiElement[] refs,
                                      ReferenceData[] referenceData) {
    PsiManager manager = PsiManager.getInstance(project);

    ArrayList<Object> array = new ArrayList<>();
    Object[] refObjects = new Object[refs.length];
    for (int i = 0; i < referenceData.length; i++) {
      PsiElement ref = refs[i];
      if (ref != null) {
        LOG.assertTrue(ref.isValid());
        ReferenceData data = referenceData[i];
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
    Arrays.sort(selectedObjects, (o1, o2) -> getFQName(o1).compareToIgnoreCase(getFQName(o2)));

    RestoreReferencesDialog dialog = new RestoreReferencesDialog(project, selectedObjects);
    dialog.show();
    selectedObjects = dialog.getSelectedElements();

    for (int i = 0; i < referenceData.length; i++) {
      PsiElement ref = refs[i];
      if (ref != null) {
        PsiUtilCore.ensureValid(ref);
        Object refObject = refObjects[i];
        boolean found = false;
        for (Object selected : selectedObjects) {
          if (Comparing.equal(refObject, selected)) {
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