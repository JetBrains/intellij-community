/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringTokenizer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class DocumentFoldingInfo implements JDOMExternalizable, CodeFoldingState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.DocumentFoldingInfo");
  private static final Key<FoldingInfo> FOLDING_INFO_KEY = Key.create("FOLDING_INFO");

  @NotNull private final Project myProject;
  private final VirtualFile myFile;

  private static class SerializedPsiElement {
    private final String mySerializedElement;
    private final FoldingInfo myFoldingInfo;
    public SerializedPsiElement(@NotNull String serialized, @NotNull FoldingInfo foldingInfo) {
      mySerializedElement = serialized;
      myFoldingInfo = foldingInfo;
    }
  }
  @NotNull private final List<SmartPsiElementPointer<PsiElement>> myPsiElements = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull private final List<SerializedPsiElement> mySerializedElements = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull private final List<RangeMarker> myRangeMarkers = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final String DEFAULT_PLACEHOLDER = "...";
  @NonNls private static final String ELEMENT_TAG = "element";
  @NonNls private static final String SIGNATURE_ATT = "signature";
  @NonNls private static final String EXPANDED_ATT = "expanded";
  @NonNls private static final String MARKER_TAG = "marker";
  @NonNls private static final String DATE_ATT = "date";
  @NonNls private static final String PLACEHOLDER_ATT = "placeholder";

  DocumentFoldingInfo(@NotNull Project project, @NotNull Document document) {
    myProject = project;
    myFile = FileDocumentManager.getInstance().getFile(document);
  }

  void loadFromEditor(@NotNull Editor editor) {
    assertDispatchThread();
    LOG.assertTrue(!editor.isDisposed());
    clear();

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    documentManager.commitDocument(editor.getDocument());
    PsiFile file = documentManager.getPsiFile(editor.getDocument());

    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    EditorFoldingInfo info = EditorFoldingInfo.get(editor);
    FoldRegion[] foldRegions = editor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : foldRegions) {
      if (!region.isValid()) continue;
      PsiElement element = info.getPsiElement(region);
      boolean expanded = region.isExpanded();
      boolean collapseByDefault = element != null &&
                                  FoldingPolicy.isCollapseByDefault(element) &&
                                  !FoldingUtil.caretInsideRange(editor, TextRange.create(region));
      if (collapseByDefault == expanded || element == null) {
        FoldingInfo fi = new FoldingInfo(region.getPlaceholderText(), expanded);
        if (element != null) {
          myPsiElements.add(smartPointerManager.createSmartPsiElementPointer(element, file));
          element.putUserData(FOLDING_INFO_KEY, fi);
        }
        else {
          RangeMarker marker = editor.getDocument().createRangeMarker(region.getStartOffset(), region.getEndOffset());
          myRangeMarkers.add(marker);
          marker.putUserData(FOLDING_INFO_KEY, fi);
        }
      }
    }
  }

  private static void assertDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread();
  }

  @Override
  public void setToEditor(@NotNull final Editor editor) {
    assertDispatchThread();
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (psiManager.isDisposed()) return;

    if (!myFile.isValid()) return;
    final PsiFile psiFile = psiManager.findFile(myFile);
    if (psiFile == null) return;

    if (!mySerializedElements.isEmpty()) {
      // Restore postponed state
      assert myPsiElements.isEmpty() : "Sequential deserialization";
      for (SerializedPsiElement entry : mySerializedElements) {
        PsiElement restoredElement = FoldingPolicy.restoreBySignature(psiFile, entry.mySerializedElement);
        if (restoredElement != null && restoredElement.isValid()) {
          myPsiElements.add(SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(restoredElement));
          restoredElement.putUserData(FOLDING_INFO_KEY, entry.myFoldingInfo);
        }
      }
      mySerializedElements.clear();
    }

    Map<PsiElement, FoldingDescriptor> ranges = null;
    for (SmartPsiElementPointer<PsiElement> ptr: myPsiElements) {
      PsiElement element = ptr.getElement();
      if (element == null || !element.isValid()) {
        continue;
      }

      if (ranges == null) {
        ranges = buildRanges(editor, psiFile);
      }
      FoldingDescriptor descriptor = ranges.get(element);
      if (descriptor == null) {
        continue;
      }

      TextRange range = descriptor.getRange();
      FoldRegion region = FoldingUtil.findFoldRegion(editor, range.getStartOffset(), range.getEndOffset());
      if (region != null) {
        FoldingInfo fi = element.getUserData(FOLDING_INFO_KEY);
        boolean state = fi != null && fi.expanded;
        region.setExpanded(state);
      }
    }
    for (RangeMarker marker : myRangeMarkers) {
      if (!marker.isValid() || marker.getStartOffset() == marker.getEndOffset()) {
        continue;
      }
      FoldRegion region = FoldingUtil.findFoldRegion(editor, marker.getStartOffset(), marker.getEndOffset());
      FoldingInfo info = marker.getUserData(FOLDING_INFO_KEY);
      if (region == null) {
        if (info != null) {
          region = editor.getFoldingModel().addFoldRegion(marker.getStartOffset(), marker.getEndOffset(), info.placeHolder);
        }
        if (region == null) {
          return;
        }
      }

      boolean state = info != null && info.expanded;
      region.setExpanded(state);
    }
  }

  @NotNull
  private static Map<PsiElement, FoldingDescriptor> buildRanges(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psiFile.getLanguage());
    final ASTNode node = psiFile.getNode();
    if (node == null) return Collections.emptyMap();
    final FoldingDescriptor[] descriptors = LanguageFolding.buildFoldingDescriptors(foldingBuilder, psiFile, editor.getDocument(), true);
    Map<PsiElement, FoldingDescriptor> ranges = new HashMap<PsiElement, FoldingDescriptor>();
    for (FoldingDescriptor descriptor : descriptors) {
      final ASTNode ast = descriptor.getElement();
      final PsiElement psi = ast.getPsi();
      if (psi != null) {
        ranges.put(psi, descriptor);
      }
    }
    return ranges;
  }

  void clear() {
    myPsiElements.clear();
    for (RangeMarker marker : myRangeMarkers) {
      marker.dispose();
    }
    myRangeMarkers.clear();
    mySerializedElements.clear();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (myPsiElements.isEmpty() && myRangeMarkers.isEmpty() && mySerializedElements.isEmpty()){
      throw new WriteExternalException();
    }

    if (mySerializedElements.isEmpty()) {
      for (SmartPsiElementPointer<PsiElement> ptr : myPsiElements) {
        PsiElement psiElement = ptr.getElement();
        if (psiElement == null || !psiElement.isValid()) {
          continue;
        }
        FoldingInfo fi = psiElement.getUserData(FOLDING_INFO_KEY);
        boolean state = fi != null && fi.expanded;
        String signature = FoldingPolicy.getSignature(psiElement);
        if (signature == null) {
          continue;
        }

        PsiFile containingFile = psiElement.getContainingFile();
        PsiElement restoredElement = FoldingPolicy.restoreBySignature(containingFile, signature);
        if (!psiElement.equals(restoredElement)) {
          StringBuilder trace = new StringBuilder();
          PsiElement restoredAgain = FoldingPolicy.restoreBySignature(containingFile, signature, trace);
          LOG.error("element: " + psiElement + "(" + psiElement.getText() 
                    + "); restoredElement: " + restoredElement
                    + "; signature: '" + signature 
                    + "'; file: " + containingFile 
                    + "; injected: " + InjectedLanguageManager.getInstance(myProject).isInjectedFragment(containingFile) 
                    + "; languages: " + containingFile.getViewProvider().getLanguages()                    
                    + "; restored again: " + restoredAgain +
                    "; restore produces same results: " + (restoredAgain == restoredElement) 
                    + "; trace:\n" + trace);
        }

        Element e = new Element(ELEMENT_TAG);
        e.setAttribute(SIGNATURE_ATT, signature);
        e.setAttribute(EXPANDED_ATT, Boolean.toString(state));
        element.addContent(e);
      }
    }
    else {
      // get back postponed state (before folding initialization)
      for (SerializedPsiElement entry : mySerializedElements) {
        Element e = new Element(ELEMENT_TAG);
        e.setAttribute(SIGNATURE_ATT, entry.mySerializedElement);
        e.setAttribute(EXPANDED_ATT, Boolean.toString(entry.myFoldingInfo.getExpanded()));
        element.addContent(e);
      }
    }
    String date = null;
    for (RangeMarker marker : myRangeMarkers) {
      FoldingInfo fi = marker.getUserData(FOLDING_INFO_KEY);
      boolean state = fi != null && fi.expanded;

      Element e = new Element(MARKER_TAG);
      if (date == null) {
        date = getTimeStamp();
      }
      if (date.isEmpty()) {
        continue;
      }

      e.setAttribute(DATE_ATT, date);
      e.setAttribute(EXPANDED_ATT, Boolean.toString(state));
      String signature = Integer.valueOf(marker.getStartOffset()) + ":" + Integer.valueOf(marker.getEndOffset());
      e.setAttribute(SIGNATURE_ATT, signature);
      String placeHolderText = fi == null ? DEFAULT_PLACEHOLDER : fi.placeHolder;
      e.setAttribute(PLACEHOLDER_ATT, placeHolderText);
      element.addContent(e);
    }
  }

  @Override
  public void readExternal(final Element element) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        clear();

        if (!myFile.isValid()) return;

        final Document document = FileDocumentManager.getInstance().getDocument(myFile);
        if (document == null) return;

        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (psiFile == null || !psiFile.getViewProvider().isPhysical()) return;

        String date = null;
        boolean canRestoreElement = !DumbService.getInstance(myProject).isDumb() || FoldingUpdate.supportsDumbModeFolding(psiFile);
        for (final Object o : element.getChildren()) {
          Element e = (Element)o;
          Boolean expanded = Boolean.valueOf(e.getAttributeValue(EXPANDED_ATT));
          if (ELEMENT_TAG.equals(e.getName())) {
            String signature = e.getAttributeValue(SIGNATURE_ATT);
            if (signature == null) {
              continue;
            }
            FoldingInfo fi = new FoldingInfo(DEFAULT_PLACEHOLDER, expanded);
            if (canRestoreElement) {
              PsiElement restoredElement = FoldingPolicy.restoreBySignature(psiFile, signature);
              if (restoredElement != null && restoredElement.isValid()) {
                myPsiElements.add(SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(restoredElement));
                restoredElement.putUserData(FOLDING_INFO_KEY, fi);
              }
            }
            else {
              // Postponed initialization
              mySerializedElements.add(new SerializedPsiElement(signature, fi));
            }
          }
          else if (MARKER_TAG.equals(e.getName())) {
            if (date == null) {
              date = getTimeStamp();
            }
            if (date.isEmpty()) continue;

            if (!date.equals(e.getAttributeValue(DATE_ATT)) || FileDocumentManager.getInstance().isDocumentUnsaved(document)) continue;
            StringTokenizer tokenizer = new StringTokenizer(e.getAttributeValue(SIGNATURE_ATT), ":");
            try {
              int start = Integer.valueOf(tokenizer.nextToken()).intValue();
              int end = Integer.valueOf(tokenizer.nextToken()).intValue();
              if (start < 0 || end >= document.getTextLength() || start > end) continue;
              RangeMarker marker = document.createRangeMarker(start, end);
              myRangeMarkers.add(marker);
              String placeHolderText = e.getAttributeValue(PLACEHOLDER_ATT);
              if (placeHolderText == null) placeHolderText = DEFAULT_PLACEHOLDER;
              FoldingInfo fi = new FoldingInfo(placeHolderText, expanded);
              marker.putUserData(FOLDING_INFO_KEY, fi);
            }
            catch (NoSuchElementException exc) {
              LOG.error(exc);
            }
          }
          else {
            throw new IllegalStateException("unknown tag: " + e.getName());
          }
        }
      }
    });
  }

  private String getTimeStamp() {
    if (!myFile.isValid()) return "";
    return Long.toString(myFile.getTimeStamp());
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + (myFile != null ? myFile.hashCode() : 0);
    result = 31 * result + myPsiElements.hashCode();
    result = 31 * result + myRangeMarkers.hashCode();
    result = 31 * result + mySerializedElements.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DocumentFoldingInfo info = (DocumentFoldingInfo)o;

    if (myFile != null ? !myFile.equals(info.myFile) : info.myFile != null) {
      return false;
    }
    if (!myProject.equals(info.myProject)
        || !myPsiElements.equals(info.myPsiElements)
        || !mySerializedElements.equals(info.mySerializedElements)) {
      return false;
    }

    if (myRangeMarkers.size() != info.myRangeMarkers.size()) return false;
    for (int i = 0; i < myRangeMarkers.size(); i++) {
      RangeMarker marker = myRangeMarkers.get(i);
      RangeMarker other = info.myRangeMarkers.get(i);
      if (marker == other || !marker.isValid() || !other.isValid()) {
        continue;
      }
      if (!TextRange.areSegmentsEqual(marker, other)) return false;

      FoldingInfo fi = marker.getUserData(FOLDING_INFO_KEY);
      FoldingInfo ofi = other.getUserData(FOLDING_INFO_KEY);
      if (!Comparing.equal(fi, ofi)) return false;
    }
    return true;
  }

  private static class FoldingInfo {
    private final String placeHolder;
    private final boolean expanded;

    private FoldingInfo(@NotNull String placeHolder, boolean expanded) {
      this.placeHolder = placeHolder;
      this.expanded = expanded;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      FoldingInfo info = (FoldingInfo)o;

      return expanded == info.expanded && placeHolder.equals(info.placeHolder);
    }

    @Override
    public int hashCode() {
      int result = placeHolder.hashCode();
      result = 31 * result + (expanded ? 1 : 0);
      return result;
    }

    public boolean getExpanded() {
      return expanded;
    }
  }
}
