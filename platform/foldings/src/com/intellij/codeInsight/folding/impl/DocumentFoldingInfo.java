// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.folding.impl;

import com.intellij.formatting.visualLayer.VisualFormattingLayerService;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyFactory;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FreezableArrayList;
import com.intellij.util.text.StringTokenizer;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static com.intellij.openapi.editor.impl.FoldingKeys.ZOMBIE_REGION_KEY;

final class DocumentFoldingInfo implements CodeFoldingState {
  private static final Logger LOG = Logger.getInstance(DocumentFoldingInfo.class);
  private static final Key<FoldingInfo> FOLDING_INFO_KEY = Key.create("FOLDING_INFO");

  private final @NotNull Project myProject;
  private final VirtualFile myFile;

  private final @NotNull List<SignatureInfo> myInfos = ContainerUtil.createLockFreeCopyOnWriteList();
  private final @NotNull List<RangeMarker> myRangeMarkers = ContainerUtil.createLockFreeCopyOnWriteList();
  /**
   * null means {@link #computeExpandRanges()} was not called yet
   */
  private volatile @Nullable @Unmodifiable List<FoldingInfo> myComputedInfos;
  private static final String DEFAULT_PLACEHOLDER = "...";
  private static final @NonNls String ELEMENT_TAG = "element";
  private static final @NonNls String SIGNATURE_ATT = "signature";
  private static final @NonNls String EXPANDED_ATT = "expanded";
  private static final @NonNls String MARKER_TAG = "marker";
  private static final @NonNls String DATE_ATT = "date";
  private static final @NonNls String PLACEHOLDER_ATT = "ph";

  DocumentFoldingInfo(@NotNull Project project, @NotNull Document document) {
    myProject = project;
    myFile = FileDocumentManager.getInstance().getFile(document);
  }

  void loadFromEditor(@NotNull Editor editor) {
    EditorThreading.Companion.assertInteractionAllowed();
    LOG.assertTrue(!editor.isDisposed());
    clear();

    int caretOffset = editor.getCaretModel().getOffset();
    FoldRegion[] foldRegions = editor.getFoldingModel().getAllFoldRegions();
    FreezableArrayList<FoldingInfo> computedInfos = new FreezableArrayList<>(foldRegions.length);
    for (FoldRegion region : foldRegions) {
      if (!region.isValid() || region.shouldNeverExpand() || CodeFoldingManagerImpl.isTransient(region)) {
        continue;
      }
      boolean expanded = region.isExpanded();
      String signature = region.getUserData(UpdateFoldRegionsOperation.SIGNATURE);
      if (Strings.areSameInstance(signature, UpdateFoldRegionsOperation.NO_SIGNATURE)) {
        continue;
      }
      Boolean storedCollapseByDefault = CodeFoldingManagerImpl.getCollapsedByDefault(region);
      boolean collapseByDefault = storedCollapseByDefault != null && storedCollapseByDefault &&
                                  !UpdateFoldRegionsOperation.caretInsideRange(caretOffset, region.getTextRange());
      if (collapseByDefault == expanded || isManuallyCreated(region, signature)) {
        if (signature == null) {
          addRangeMarker(editor.getDocument(), region.getTextRange(), expanded, region.getPlaceholderText());
        }
        else {
          myInfos.add(new SignatureInfo(signature, expanded));
        }
      }
      computedInfos.add(new FoldingInfo(region.getPlaceholderText(), region.getTextRange(), expanded));
    }
    myComputedInfos = computedInfos.emptyOrFrozen();
  }

  private void addRangeMarker(@NotNull Document document, @NotNull TextRange range, boolean expanded, @NotNull String placeholderText) {
    RangeMarker marker = document.createRangeMarker(range);
    myRangeMarkers.add(marker);
    marker.putUserData(FOLDING_INFO_KEY, new FoldingInfo(placeholderText, range, expanded));
  }

  private static boolean isManuallyCreated(@NotNull FoldRegion region, @Nullable String signature) {
    return signature == null && !CodeFoldingManagerImpl.isAutoCreated(region);
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  void computeExpandRanges() {
    ThreadingAssertions.assertBackgroundThread();
    ThreadingAssertions.assertReadAccess();

    PsiManager psiManager;
    PsiFile psiFile;
    Document document;
    if (myProject.isDisposed()
        || (psiManager = PsiManager.getInstance(myProject)).isDisposed()
        || !myFile.isValid()
        || (psiFile = psiManager.findFile(myFile)) == null
        || !(psiFile = psiFile instanceof PsiCompiledFile compiled ? compiled.getDecompiledPsiFile() : psiFile).isValid()
        || (document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile)) == null
    ) {
      myComputedInfos = List.of();
      return;
    }
    List<FoldingInfo> result = new ArrayList<>(myRangeMarkers.size() + myInfos.size());
    Map<PsiElement, FoldingDescriptor> ranges = null;
    for (SignatureInfo info : myInfos) {
      PsiElement element = FoldingPolicy.restoreBySignature(psiFile, info.signature);
      if (element == null || !element.isValid()) {
        continue;
      }

      if (ranges == null) {
        ranges = buildRanges(psiFile, document);
      }
      FoldingDescriptor descriptor = ranges.get(element);
      if (descriptor == null) {
        continue;
      }

      TextRange range = descriptor.getRange();
      result.add(new FoldingInfo(ObjectUtils.notNull(descriptor.getPlaceholderText(),DEFAULT_PLACEHOLDER), range, info.expanded));
    }
    for (RangeMarker marker : myRangeMarkers) {
      if (!marker.isValid() || marker.getStartOffset() == marker.getEndOffset()) {
        continue;
      }
      FoldingInfo info = marker.getUserData(FOLDING_INFO_KEY);
      if (info != null) {
        result.add(info);
      }
    }
    myComputedInfos = ContainerUtil.createConcurrentList(result);
  }

  @RequiresEdt
  void applyFoldingExpandedState(@NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    assert !editor.isDisposed();

    List<FoldingInfo> computedInfos = myComputedInfos;
    if (computedInfos == null) {
      throw new IllegalStateException("Must call computeExpandRanges() before calling applyFoldingExpandedState()");
    }
    for (FoldingInfo foldingInfo : computedInfos) {
      TextRange range = foldingInfo.textRange();
      FoldRegion region = FoldingUtil.findFoldRegion(editor, range.getStartOffset(), range.getEndOffset());
      if (region != null) {
        expandRegionBlessForNewLife(editor, region, foldingInfo.expanded());
      }
    }
    myComputedInfos = List.of();
  }

  @RequiresEdt
  @Override
  public void setToEditor(@NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();

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

      boolean expanded = info != null && info.expanded;
      expandRegionBlessForNewLife(editor, region, expanded);
    }
    applyFoldingExpandedState(editor);
  }

  private static void expandRegionBlessForNewLife(@NotNull Editor editor, @NotNull FoldRegion region, boolean expanded) {
    if (!CodeFoldingManagerImpl.isFoldingsInitializedInEditor(editor)) {
      int offset = editor.getCaretModel().getOffset();
      if (offset > region.getStartOffset() && offset < region.getEndOffset()) {
        // the editor is not initialized, but the caret is already moved into the fold region.
        expanded = true;
      }
    }
    ZOMBIE_REGION_KEY.set(region, null);
    region.setExpanded(expanded);
  }

  private static @NotNull Map<PsiElement, FoldingDescriptor> buildRanges(@NotNull PsiFile psiFile, @NotNull Document document) {
    FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psiFile.getLanguage());
    ASTNode node = psiFile.getNode();
    if (node == null) {
      return Collections.emptyMap();
    }
    FoldingDescriptor[] descriptors = LanguageFolding.buildFoldingDescriptors(foldingBuilder, psiFile, document, true);
    Map<PsiElement, FoldingDescriptor> ranges = HashMap.newHashMap(descriptors.length);
    for (FoldingDescriptor descriptor : descriptors) {
      ASTNode ast = descriptor.getElement();
      PsiElement psi = ast.getPsi();
      if (psi != null) {
        ranges.put(psi, descriptor);
      }
    }
    return ranges;
  }

  void clear() {
    myInfos.clear();
    for (RangeMarker marker : myRangeMarkers) {
      marker.dispose();
    }
    myRangeMarkers.clear();
  }

  void writeExternal(@NotNull Element element) {
    if (myInfos.isEmpty() && myRangeMarkers.isEmpty()) {
      return;
    }

    for (SignatureInfo info : myInfos) {
      Element e = new Element(ELEMENT_TAG);
      e.setAttribute(SIGNATURE_ATT, info.signature);
      if (info.expanded) {
        e.setAttribute(EXPANDED_ATT, Boolean.toString(true));
      }
      element.addContent(e);
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
      String signature = marker.getStartOffset() + ":" + marker.getEndOffset();
      e.setAttribute(SIGNATURE_ATT, signature);
      String placeHolderText = fi == null ? DEFAULT_PLACEHOLDER : fi.placeHolder;
      e.setAttribute(PLACEHOLDER_ATT, XmlStringUtil.escapeIllegalXmlChars(placeHolderText));
      element.addContent(e);
    }
  }

  void readExternal(@NotNull Element element) {
    ApplicationManager.getApplication().runReadAction(() -> {
      clear();

      if (!myFile.isValid()) {
        return;
      }

      Document document = FileDocumentManager.getInstance().getDocument(myFile);
      if (document == null) {
        return;
      }

      // IDEA-313274 Remove persisted visual formatting foldings from workspace files
      WhiteSpaceFormattingStrategy whiteSpaceFormattingStrategy = WhiteSpaceFormattingStrategyFactory.DEFAULT_STRATEGY;
      boolean removeVFmtZombieFoldings = VisualFormattingLayerService.shouldRemoveZombieFoldings();

      String date = null;
      for (Element e : element.getChildren()) {
        String signature = e.getAttributeValue(SIGNATURE_ATT);
        if (signature == null) {
          continue;
        }

        boolean expanded = Boolean.parseBoolean(e.getAttributeValue(EXPANDED_ATT));
        if (ELEMENT_TAG.equals(e.getName())) {
          myInfos.add(new SignatureInfo(signature, expanded));
        }
        else if (MARKER_TAG.equals(e.getName())) {
          if (date == null) {
            date = getTimeStamp();
          }
          if (date.isEmpty()) {
            continue;
          }

          if (!date.equals(e.getAttributeValue(DATE_ATT)) || FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
            continue;
          }

          StringTokenizer tokenizer = new StringTokenizer(signature, ":");
          try {
            int start = Integer.parseInt(tokenizer.nextToken());
            int end = Integer.parseInt(tokenizer.nextToken());
            if (start < 0 || end >= document.getTextLength() || start > end) continue;
            String placeholderAttributeValue = e.getAttributeValue(PLACEHOLDER_ATT);
            String placeHolderText = placeholderAttributeValue == null ? DEFAULT_PLACEHOLDER
                                                                       : XmlStringUtil.unescapeIllegalXmlChars(placeholderAttributeValue);

            // IDEA-313274 Remove persisted visual formatting foldings from workspace files
            if (removeVFmtZombieFoldings &&
                placeHolderText.isEmpty() &&
                whiteSpaceFormattingStrategy.check(document.getText(), start, end) >= end) {
              continue;
            }

            addRangeMarker(document, TextRange.create(start, end), expanded, placeHolderText);
          }
          catch (NoSuchElementException exc) {
            LOG.error(exc);
          }
        }
        else {
          throw new IllegalStateException("unknown tag: " + e.getName());
        }
      }
    });
  }

  private @NotNull String getTimeStamp() {
    return myFile.isValid() ? Long.toString(myFile.getTimeStamp()) : "";
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + (myFile != null ? myFile.hashCode() : 0);
    result = 31 * result + myInfos.hashCode();
    result = 31 * result + myRangeMarkers.hashCode();
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

    if (!Objects.equals(myFile, info.myFile)) {
      return false;
    }
    if (!myProject.equals(info.myProject) || !myInfos.equals(info.myInfos)) {
      return false;
    }

    if (myRangeMarkers.size() != info.myRangeMarkers.size()) {
      return false;
    }
    for (int i = 0; i < myRangeMarkers.size(); i++) {
      RangeMarker marker = myRangeMarkers.get(i);
      RangeMarker other = info.myRangeMarkers.get(i);
      if (marker == other || !marker.isValid() || !other.isValid()) {
        continue;
      }
      if (!TextRange.areSegmentsEqual(marker, other)) {
        return false;
      }

      FoldingInfo fi = marker.getUserData(FOLDING_INFO_KEY);
      FoldingInfo ofi = other.getUserData(FOLDING_INFO_KEY);
      if (!Comparing.equal(fi, ofi)) {
        return false;
      }
    }
    return true;
  }

  private record SignatureInfo(@NotNull String signature, boolean expanded) {
  }

  private record FoldingInfo(@NotNull String placeHolder, @NotNull TextRange textRange, boolean expanded) {
  }
}
