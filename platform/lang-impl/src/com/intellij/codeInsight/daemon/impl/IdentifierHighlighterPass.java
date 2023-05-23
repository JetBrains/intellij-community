// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.*;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.model.Symbol;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.codeInsight.daemon.impl.HighlightInfoType.ELEMENT_UNDER_CARET_STRUCTURAL;
import static com.intellij.codeInsight.highlighting.HighlightUsagesKt.getUsageRanges;
import static com.intellij.model.psi.impl.TargetsKt.targetSymbols;


public class IdentifierHighlighterPass {
  private static final Logger LOG = Logger.getInstance(IdentifierHighlighterPass.class);

  private final PsiFile myFile;
  private final Editor myEditor;
  private final Collection<TextRange> myReadAccessRanges = Collections.synchronizedSet(new LinkedHashSet<>());
  private final Collection<TextRange> myWriteAccessRanges = Collections.synchronizedSet(new LinkedHashSet<>());
  private final Collection<TextRange> myCodeBlockMarkerRanges = Collections.synchronizedSet(new LinkedHashSet<>());
  private final int myCaretOffset;
  private final ProperTextRange myVisibleRange;

  /**
   * @param file may be injected fragment, in which case the {@code editor} must be corresponding injected editor and  {@code visibleRange} must have consistent offsets inside the injected document.
   * In both cases, {@link #doCollectInformation()} will produce and {@link #doApplyInformationToEditor()} will apply HighlightInfos for the host file.
   */
  IdentifierHighlighterPass(@NotNull PsiFile file, @NotNull Editor editor, @NotNull TextRange visibleRange) {
    myFile = file;
    myEditor = editor;
    myCaretOffset = myEditor.getCaretModel().getOffset();
    myVisibleRange = new ProperTextRange(visibleRange);
  }

  public void doCollectInformation() {
    HighlightUsagesHandlerBase<PsiElement> highlightUsagesHandler = HighlightUsagesHandler.createCustomHandler(myEditor, myFile, myVisibleRange);
    if (highlightUsagesHandler != null) {
      List<PsiElement> targets = highlightUsagesHandler.getTargets();
      highlightUsagesHandler.computeUsages(targets);
      List<TextRange> readUsages = highlightUsagesHandler.getReadUsages();
      for (TextRange readUsage : readUsages) {
        LOG.assertTrue(readUsage != null, "null text range from " + highlightUsagesHandler);
      }
      myReadAccessRanges.addAll(readUsages);
      List<TextRange> writeUsages = highlightUsagesHandler.getWriteUsages();
      for (TextRange writeUsage : writeUsages) {
        LOG.assertTrue(writeUsage != null, "null text range from " + highlightUsagesHandler);
      }
      myWriteAccessRanges.addAll(writeUsages);
      if (!highlightUsagesHandler.highlightReferences()) return;
    }

    collectCodeBlockMarkerRanges();
    highlightReferencesAndDeclarations();
  }

  @ApiStatus.Internal
  public Collection<TextRange> getReadAccessRange() {
    return myReadAccessRanges;
  }

  @ApiStatus.Internal
  public Collection<TextRange> getWriteAccessRange() {
    return myWriteAccessRanges;
  }

  @ApiStatus.Internal
  public Collection<TextRange> getCodeBlockMarkerRanges() {
    return myCodeBlockMarkerRanges;
  }

  /**
   * Collects code block markers ranges to highlight. E.g. if/elsif/else. Collected ranges will be highlighted the same way as braces
   */
  private void collectCodeBlockMarkerRanges() {
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFile.getProject());

    PsiElement contextElement = myFile.findElementAt(
      TargetElementUtil.adjustOffset(myFile, myEditor.getDocument(), myEditor.getCaretModel().getOffset()));
    if (contextElement == null) {
      return;
    }

    for (TextRange range : CodeBlockSupportHandler.findMarkersRanges(contextElement)) {
      myCodeBlockMarkerRanges.add(manager.injectedToHost(contextElement, range));
    }
  }

  /**
   * Returns read and write usages of psi element inside a single element
   *
   * @param target target psi element
   * @param psiElement psi element to search in
   */
  public static void getHighlightUsages(@NotNull PsiElement target,
                                        @NotNull PsiElement psiElement,
                                        boolean withDeclarations,
                                        @NotNull Collection<? super TextRange> readRanges,
                                        @NotNull Collection<? super TextRange> writeRanges) {
    getUsages(target, psiElement, withDeclarations, true, readRanges, writeRanges);
  }

  /**
   * Returns usages of psi element inside a single element
   * @param target target psi element
   * @param psiElement psi element to search in
   */
  @NotNull
  public static Collection<TextRange> getUsages(@NotNull PsiElement target, PsiElement psiElement, boolean withDeclarations) {
    List<TextRange> ranges = new ArrayList<>();
    getUsages(target, psiElement, withDeclarations, false, ranges, ranges);
    return ranges;
  }

  private static void getUsages(@NotNull PsiElement target,
                                @NotNull PsiElement scopeElement,
                                boolean withDeclarations,
                                boolean detectAccess,
                                @NotNull Collection<? super TextRange> readRanges,
                                @NotNull Collection<? super TextRange> writeRanges) {
    ReadWriteAccessDetector detector = detectAccess ? ReadWriteAccessDetector.findDetector(target) : null;
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(target.getProject())).getFindUsagesManager();
    FindUsagesHandler findUsagesHandler = findUsagesManager.getFindUsagesHandler(target, true);
    LocalSearchScope scope = new LocalSearchScope(scopeElement);
    Collection<PsiReference> refs = findUsagesHandler == null
                                    ? ReferencesSearch.search(target, scope).findAll()
                                    : findUsagesHandler.findReferencesToHighlight(target, scope);
    for (PsiReference psiReference : refs) {
      if (psiReference == null) {
        LOG.error("Null reference returned, findUsagesHandler=" + findUsagesHandler + "; target=" + target + " of " + target.getClass());
        continue;
      }
      Collection<? super TextRange> destination;
      if (detector == null || detector.getReferenceAccess(target, psiReference) == ReadWriteAccessDetector.Access.Read) {
        destination = readRanges;
      }
      else {
        destination = writeRanges;
      }
      HighlightUsagesHandler.collectHighlightRanges(psiReference, destination);
    }

    if (withDeclarations) {
      TextRange declRange = HighlightUsagesHandler.getNameIdentifierRange(scopeElement.getContainingFile(), target);
      if (declRange != null) {
        if (detector != null && detector.isDeclarationWriteAccess(target)) {
          writeRanges.add(declRange);
        }
        else {
          readRanges.add(declRange);
        }
      }
    }
  }

  private void highlightReferencesAndDeclarations() {
    Collection<Symbol> targetSymbols = getTargetSymbols();
    for (Symbol symbol : targetSymbols) {
      highlightTargetUsages(symbol);
    }
  }

  private @NotNull Collection<@NotNull Symbol> getTargetSymbols() {
    try {
      Collection<Symbol> fromHostFile = targetSymbols(myFile, myCaretOffset);
      if (!fromHostFile.isEmpty()) {
        return fromHostFile;
      }
    }
    catch (IndexNotReadyException ignored) {
    }
    //noinspection deprecation
    Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myFile, myCaretOffset);
    PsiFile injectedFile = PsiDocumentManager.getInstance(myFile.getProject()).getPsiFile(injectedEditor.getDocument());
    if (injectedFile == null) {
      return Collections.emptyList();
    }
    int injectedOffset = injectedEditor.getCaretModel().getOffset();
    return targetSymbols(injectedFile, injectedOffset);
  }

  private void highlightTargetUsages(@NotNull Symbol target) {
    AstLoadingFilter.disallowTreeLoading(() -> {
      UsageRanges ranges = getUsageRanges(myFile, target);
      if (ranges == null) {
        return;
      }
      myReadAccessRanges.addAll(ranges.getReadRanges());
      myReadAccessRanges.addAll(ranges.getReadDeclarationRanges());
      myWriteAccessRanges.addAll(ranges.getWriteRanges());
      myWriteAccessRanges.addAll(ranges.getWriteDeclarationRanges());
    }, () -> "Currently highlighted file: \n" +
             "psi file: " + myFile + ";\n" +
             "virtual file: " + myFile.getVirtualFile());
  }

  private static int id;
  private int getId() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int id = IdentifierHighlighterPass.id;
    if (id == 0) {
      IdentifierHighlighterPass.id = id = ((TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(
        myFile.getProject())).getNextAvailableId();
    }
    return id;
  }

  public void doApplyInformationToEditor() {
    boolean virtSpace = EditorUtil.isCaretInVirtualSpace(myEditor);
    List<HighlightInfo> infos = virtSpace || isCaretOverCollapsedFoldRegion() ? Collections.emptyList() : getHighlights();
    PsiFile hostFile = InjectedLanguageManager.getInstance(myFile.getProject()).getTopLevelFile(myFile);
    Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(myEditor);
    UpdateHighlightersUtil.setHighlightersToSingleEditor(myFile.getProject(), hostEditor, 0, hostFile.getTextLength(), infos, hostEditor.getColorsScheme(), getId());
    doAdditionalCodeBlockHighlighting();
  }

  private boolean isCaretOverCollapsedFoldRegion() {
    return myEditor.getFoldingModel().getCollapsedRegionAtOffset(myEditor.getCaretModel().getOffset()) != null;
  }

  /**
   * Does additional work on code block markers highlighting: <ul>
   * <li>Draws vertical line covering the scope on the gutter by {@link BraceHighlightingHandler#lineMarkFragment(EditorEx, Document, int, int, boolean)}</li>
   * <li>Schedules preview of the block start if necessary by {@link BraceHighlightingHandler#showScopeHint(Editor, PsiFile, int, int)}</li>
   * </ul>
   *
   * In brace matching case this is done from {@link BraceHighlightingHandler#highlightBraces(TextRange, TextRange, boolean, boolean, com.intellij.openapi.fileTypes.FileType)}
   */
  private void doAdditionalCodeBlockHighlighting() {
    if (myCodeBlockMarkerRanges.size() < 2 || !(myEditor instanceof EditorEx)) {
      return;
    }
    List<TextRange> markers = new ArrayList<>(myCodeBlockMarkerRanges);
    markers.sort(Segment.BY_START_OFFSET_THEN_END_OFFSET);
    TextRange leftBraceRange = markers.get(0);
    TextRange rightBraceRange = markers.get(markers.size() - 1);
    int startLine = myEditor.offsetToLogicalPosition(leftBraceRange.getStartOffset()).line;
    int endLine = myEditor.offsetToLogicalPosition(rightBraceRange.getEndOffset()).line;
    if (endLine - startLine > 0) {
      BraceHighlightingHandler.lineMarkFragment((EditorEx)myEditor, myEditor.getDocument(), startLine, endLine, true);
    }

    BraceHighlightingHandler.showScopeHint(myEditor, myFile, leftBraceRange.getStartOffset(), leftBraceRange.getEndOffset());
  }

  @NotNull
  private List<HighlightInfo> getHighlights() {
    if (myReadAccessRanges.isEmpty() && myWriteAccessRanges.isEmpty() && myCodeBlockMarkerRanges.isEmpty()) {
      return Collections.emptyList();
    }
    Set<Pair<Object, TextRange>> existingMarkupTooltips = new HashSet<>();
    for (RangeHighlighter highlighter : myEditor.getMarkupModel().getAllHighlighters()) {
      existingMarkupTooltips.add(Pair.create(highlighter.getErrorStripeTooltip(), highlighter.getTextRange()));
    }

    List<HighlightInfo> result = new ArrayList<>(myReadAccessRanges.size() + myWriteAccessRanges.size() + myCodeBlockMarkerRanges.size());
    for (TextRange range: myReadAccessRanges) {
      ContainerUtil.addIfNotNull(result, createHighlightInfo(range, HighlightInfoType.ELEMENT_UNDER_CARET_READ, existingMarkupTooltips));
    }
    for (TextRange range: myWriteAccessRanges) {
      ContainerUtil.addIfNotNull(result, createHighlightInfo(range, HighlightInfoType.ELEMENT_UNDER_CARET_WRITE, existingMarkupTooltips));
    }
    if (CodeInsightSettings.getInstance().HIGHLIGHT_BRACES) {
      myCodeBlockMarkerRanges.forEach(
        it -> ContainerUtil.addIfNotNull(result, createHighlightInfo(it, ELEMENT_UNDER_CARET_STRUCTURAL, existingMarkupTooltips)));
    }

    return result;
  }

  @NotNull
  private HighlightInfo createHighlightInfo(@NotNull TextRange range, @NotNull HighlightInfoType type, @NotNull Set<Pair<Object, TextRange>> existingMarkupTooltips) {
    int start = range.getStartOffset();
    String tooltip = start <= myEditor.getDocument().getTextLength() ? HighlightHandlerBase.getLineTextErrorStripeTooltip(myEditor.getDocument(), start, false) : null;
    String unescapedTooltip = existingMarkupTooltips.contains(new Pair<Object, TextRange>(tooltip, range)) ? null : tooltip;
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
    if (unescapedTooltip != null) {
      builder.unescapedToolTip(unescapedTooltip);
    }
    return builder.createUnconditionally();
  }

  public static void clearMyHighlights(@NotNull Document document, @NotNull Project project) {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, true);
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
      if (info == null) continue;
      if (info.type == HighlightInfoType.ELEMENT_UNDER_CARET_READ || info.type == HighlightInfoType.ELEMENT_UNDER_CARET_WRITE) {
        highlighter.dispose();
      }
    }
  }
}
