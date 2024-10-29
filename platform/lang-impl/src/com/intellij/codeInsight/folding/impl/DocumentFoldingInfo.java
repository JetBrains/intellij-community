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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyFactory;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringTokenizer;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.editor.impl.FoldingModelImpl.ZOMBIE_BITTEN_KEY;

final class DocumentFoldingInfo implements CodeFoldingState {
  private static final Logger LOG = Logger.getInstance(DocumentFoldingInfo.class);
  private static final Key<FoldingInfo> FOLDING_INFO_KEY = Key.create("FOLDING_INFO");

  private final @NotNull Project myProject;
  private final VirtualFile file;

  private final @NotNull List<Info> myInfos = ContainerUtil.createLockFreeCopyOnWriteList();
  private final @NotNull List<RangeMarker> myRangeMarkers = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final String DEFAULT_PLACEHOLDER = "...";
  private static final @NonNls String ELEMENT_TAG = "element";
  private static final @NonNls String SIGNATURE_ATT = "signature";
  private static final @NonNls String EXPANDED_ATT = "expanded";
  private static final @NonNls String MARKER_TAG = "marker";
  private static final @NonNls String DATE_ATT = "date";
  private static final @NonNls String PLACEHOLDER_ATT = "ph";

  DocumentFoldingInfo(@NotNull Project project, @NotNull Document document) {
    myProject = project;
    file = FileDocumentManager.getInstance().getFile(document);
  }

  void loadFromEditor(@NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(!editor.isDisposed());
    clear();

    FoldRegion[] foldRegions = editor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : foldRegions) {
      if (!region.isValid() || region.shouldNeverExpand()) continue;
      boolean expanded = region.isExpanded();
      String signature = region.getUserData(UpdateFoldRegionsOperation.SIGNATURE);
      if (Strings.areSameInstance(signature, UpdateFoldRegionsOperation.NO_SIGNATURE)) continue;
      Boolean storedCollapseByDefault = region.getUserData(UpdateFoldRegionsOperation.COLLAPSED_BY_DEFAULT);
      boolean collapseByDefault = storedCollapseByDefault != null && storedCollapseByDefault &&
                                  !FoldingUtil.caretInsideRange(editor, region.getTextRange());
      if (collapseByDefault == expanded || isManuallyCreated(region, signature)) {
        if (signature != null) {
          myInfos.add(new Info(signature, expanded));
        }
        else {
          RangeMarker marker = editor.getDocument().createRangeMarker(region.getStartOffset(), region.getEndOffset());
          myRangeMarkers.add(marker);
          marker.putUserData(FOLDING_INFO_KEY, new FoldingInfo(region.getPlaceholderText(), expanded));
        }
      }
    }
  }

  private static boolean isManuallyCreated(@Nullable FoldRegion region, @Nullable String signature) {
    return signature == null && !CodeFoldingManagerImpl.isAutoCreated(region);
  }

  @Override
  public void setToEditor(final @NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (psiManager.isDisposed()) return;

    if (!file.isValid()) return;
    final PsiFile psiFile = psiManager.findFile(file);
    if (psiFile == null) return;

    Map<PsiElement, FoldingDescriptor> ranges = null;
    for (Info info : myInfos) {
      PsiElement element = FoldingPolicy.restoreBySignature(psiFile, info.signature);
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
      if (region != null && region.getUserData(ZOMBIE_BITTEN_KEY) == null) {
        region.setExpanded(info.expanded);
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

  private static @NotNull Map<PsiElement, FoldingDescriptor> buildRanges(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psiFile.getLanguage());
    final ASTNode node = psiFile.getNode();
    if (node == null) return Collections.emptyMap();
    final FoldingDescriptor[] descriptors = LanguageFolding.buildFoldingDescriptors(foldingBuilder, psiFile, editor.getDocument(), true);
    Map<PsiElement, FoldingDescriptor> ranges = new HashMap<>();
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
    myInfos.clear();
    for (RangeMarker marker : myRangeMarkers) {
      marker.dispose();
    }
    myRangeMarkers.clear();
  }

  void writeExternal(@NotNull Element element) {
    if (myInfos.isEmpty() && myRangeMarkers.isEmpty()){
      return;
    }

    for (Info info : myInfos) {
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

      if (!file.isValid()) {
        return;
      }

      Document document = FileDocumentManager.getInstance().getDocument(file);
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
          myInfos.add(new Info(signature, expanded));
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
            int start = Integer.valueOf(tokenizer.nextToken()).intValue();
            int end = Integer.valueOf(tokenizer.nextToken()).intValue();
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

            RangeMarker marker = document.createRangeMarker(start, end);
            myRangeMarkers.add(marker);
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
    });
  }

  private String getTimeStamp() {
    if (!file.isValid()) return "";
    return Long.toString(file.getTimeStamp());
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + (file != null ? file.hashCode() : 0);
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

    if (file != null ? !file.equals(info.file) : info.file != null) {
      return false;
    }
    if (!myProject.equals(info.myProject)
        || !myInfos.equals(info.myInfos)) {
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

  private record Info(@NotNull String signature, boolean expanded) {
  }

  private record FoldingInfo(@NotNull String placeHolder, boolean expanded) {
  }
}
