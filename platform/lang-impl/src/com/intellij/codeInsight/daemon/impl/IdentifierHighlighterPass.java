// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.*;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.model.search.SearchService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.daemon.impl.HighlightInfoType.ELEMENT_UNDER_CARET_STRUCTURAL;
import static com.intellij.model.psi.impl.TargetsKt.targetSymbols;

/**
 * @author yole
 */
public class IdentifierHighlighterPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance(IdentifierHighlighterPass.class);

  private final PsiFile myFile;
  private final Editor myEditor;
  private final Collection<TextRange> myReadAccessRanges = Collections.synchronizedList(new ArrayList<>());
  private final Collection<TextRange> myWriteAccessRanges = Collections.synchronizedList(new ArrayList<>());
  private final Collection<TextRange> myCodeBlockMarkerRanges = Collections.synchronizedList(new ArrayList<>());
  private final int myCaretOffset;
  private final ProperTextRange myVisibleRange;

  IdentifierHighlighterPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor) {
    super(project, editor.getDocument(), false);
    myFile = file;
    myEditor = editor;
    myCaretOffset = myEditor.getCaretModel().getOffset();
    myVisibleRange = VisibleHighlightingPassFactory.calculateVisibleRange(myEditor);
  }

  @Override
  public void doCollectInformation(@NotNull final ProgressIndicator progress) {
    final HighlightUsagesHandlerBase<PsiElement> highlightUsagesHandler = HighlightUsagesHandler.createCustomHandler(myEditor, myFile, myVisibleRange);
    if (highlightUsagesHandler != null) {
      List<PsiElement> targets = highlightUsagesHandler.getTargets();
      highlightUsagesHandler.computeUsages(targets);
      final List<TextRange> readUsages = highlightUsagesHandler.getReadUsages();
      for (TextRange readUsage : readUsages) {
        LOG.assertTrue(readUsage != null, "null text range from " + highlightUsagesHandler);
      }
      myReadAccessRanges.addAll(readUsages);
      final List<TextRange> writeUsages = highlightUsagesHandler.getWriteUsages();
      for (TextRange writeUsage : writeUsages) {
        LOG.assertTrue(writeUsage != null, "null text range from " + highlightUsagesHandler);
      }
      myWriteAccessRanges.addAll(writeUsages);
      if (!highlightUsagesHandler.highlightReferences()) return;
    }

    collectCodeBlockMarkerRanges();
    if (Registry.is("ide.symbol.identifier.highlighting")) {
      highlightReferencesAndDeclarations();
    }
    else {
      highlightReferencesAndDeclarationsOld();
    }
  }

  private void highlightReferencesAndDeclarationsOld() {
    int flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED;
    PsiElement myTarget;
    try {
      myTarget = TargetElementUtil.getInstance().findTargetElement(myEditor, flags, myCaretOffset);
    }
    catch (IndexNotReadyException e) {
      return;
    }

    if (myTarget == null) {
      if (!PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
        // when document is committed, try to check injected stuff - it's fast
        Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myFile, myCaretOffset);
        myTarget = TargetElementUtil.getInstance().findTargetElement(injectedEditor, flags, injectedEditor.getCaretModel().getOffset());
      }
    }

    if (myTarget != null) {
      highlightTargetUsages(myTarget);
    } else {
      PsiReference ref = TargetElementUtil.findReference(myEditor);
      if (ref instanceof PsiPolyVariantReference) {
        if (!ref.getElement().isValid()) {
          throw new PsiInvalidElementAccessException(ref.getElement(), "Invalid element in " + ref + " of " + ref.getClass() + "; editor=" + myEditor);
        }
        ResolveResult[] results = ((PsiPolyVariantReference)ref).multiResolve(false);
        if (results.length > 0) {
          for (ResolveResult result : results) {
            PsiElement target = result.getElement();
            if (target != null) {
              if (!target.isValid()) {
                throw new PsiInvalidElementAccessException(target, "Invalid element returned from " + ref + " of " + ref.getClass() + "; editor=" + myEditor);
              }
              highlightTargetUsages(target);
            }
          }
        }
      }

    }
  }

  /**
   * Collects code block markers ranges to highlight. E.g. if/elsif/else. Collected ranges will be highlighted the same way as braces
   */
  private void collectCodeBlockMarkerRanges() {
    PsiElement contextElement = myFile.findElementAt(
      TargetElementUtil.adjustOffset(myFile, myEditor.getDocument(), myEditor.getCaretModel().getOffset()));
    myCodeBlockMarkerRanges.addAll(CodeBlockSupportHandler.findMarkersRanges(contextElement));
  }

  /**
   * Returns read and write usages of psi element inside a single element
   *
   * @param target target psi element
   * @param psiElement psi element to search in
   * @return a pair where first element is read usages and second is write usages
   */
  @NotNull
  public static Couple<Collection<TextRange>> getHighlightUsages(@NotNull PsiElement target, PsiElement psiElement, boolean withDeclarations) {
    return getUsages(target, psiElement, withDeclarations, true);
  }

  /**
   * Returns usages of psi element inside a single element
   *
   * @param target target psi element
   * @param psiElement psi element to search in
   */
  @NotNull
  public static Collection<TextRange> getUsages(@NotNull PsiElement target, PsiElement psiElement, boolean withDeclarations) {
    return getUsages(target, psiElement, withDeclarations, false).first;
  }

  @NotNull
  private static Couple<Collection<TextRange>> getUsages(@NotNull PsiElement target, PsiElement psiElement, boolean withDeclarations, boolean detectAccess) {
    List<TextRange> readRanges = new ArrayList<>();
    List<TextRange> writeRanges = new ArrayList<>();
    final ReadWriteAccessDetector detector = detectAccess ? ReadWriteAccessDetector.findDetector(target) : null;
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(target.getProject())).getFindUsagesManager();
    final FindUsagesHandler findUsagesHandler = findUsagesManager.getFindUsagesHandler(target, true);
    final LocalSearchScope scope = new LocalSearchScope(psiElement);
    Collection<PsiReference> refs = findUsagesHandler != null
                              ? findUsagesHandler.findReferencesToHighlight(target, scope)
                              : ReferencesSearch.search(target, scope).findAll();
    for (PsiReference psiReference : refs) {
      if (psiReference == null) {
        LOG.error("Null reference returned, findUsagesHandler=" + findUsagesHandler + "; target=" + target + " of " + target.getClass());
        continue;
      }
      List<TextRange> destination;
      if (detector == null || detector.getReferenceAccess(target, psiReference) == ReadWriteAccessDetector.Access.Read) {
        destination = readRanges;
      }
      else {
        destination = writeRanges;
      }
      HighlightUsagesHandler.collectHighlightRanges(psiReference, destination);
    }

    if (withDeclarations) {
      final TextRange declRange = HighlightUsagesHandler.getNameIdentifierRange(psiElement.getContainingFile(), target);
      if (declRange != null) {
        if (detector != null && detector.isDeclarationWriteAccess(target)) {
          writeRanges.add(declRange);
        }
        else {
          readRanges.add(declRange);
        }
      }
    }

    return Couple.of(readRanges, writeRanges);
  }

  private void highlightTargetUsages(@NotNull PsiElement target) {
    final Couple<Collection<TextRange>> usages = AstLoadingFilter.disallowTreeLoading(
      () -> getHighlightUsages(target, myFile, true),
      () -> "Currently highlighted file: \n" +
            "psi file: " + myFile + ";\n" +
            "virtual file: " + myFile.getVirtualFile()
    );
    myReadAccessRanges.addAll(usages.first);
    myWriteAccessRanges.addAll(usages.second);
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
    final Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myFile, myCaretOffset);
    final PsiFile injectedFile = PsiDocumentManager.getInstance(myProject).getPsiFile(injectedEditor.getDocument());
    if (injectedFile == null) {
      return Collections.emptyList();
    }
    final int injectedOffset = injectedEditor.getCaretModel().getOffset();
    return targetSymbols(injectedFile, injectedOffset);
  }

  private void highlightTargetUsages(@NotNull Symbol target) {
    final Couple<? extends Collection<TextRange>> usages = AstLoadingFilter.disallowTreeLoading(
      () -> getUsages(myFile, target),
      () -> "Currently highlighted file: \n" +
            "psi file: " + myFile + ";\n" +
            "virtual file: " + myFile.getVirtualFile()
    );
    myReadAccessRanges.addAll(usages.first);
    myWriteAccessRanges.addAll(usages.second);
  }

  @ApiStatus.Internal
  public static @NotNull Couple<List<TextRange>> getUsages(@NotNull PsiFile file, @NotNull Symbol symbol) {
    final List<TextRange> readRanges = new ArrayList<>();
    final List<TextRange> writeRanges = new ArrayList<>();

    final SearchScope searchScope = new LocalSearchScope(file);

    final Project project = file.getProject();
    final PsiElement psiTarget = PsiSymbolService.getInstance().extractElementFromSymbol(symbol);
    final ReadWriteAccessDetector detector = psiTarget != null ? ReadWriteAccessDetector.findDetector(psiTarget) : null;

    final Collection<? extends PsiSymbolReference> refs = getReferences(project, searchScope, symbol, psiTarget);
    for (PsiSymbolReference ref : refs) {
      boolean write = detector != null &&
                      ref instanceof PsiReference &&
                      detector.getReferenceAccess(psiTarget, (PsiReference)ref) != ReadWriteAccessDetector.Access.Read;
      HighlightUsagesHandler.collectHighlightRanges(ref, write ? writeRanges : readRanges);
    }

    final Collection<? extends PsiSymbolDeclaration> declarations = SearchService.getInstance()
      .searchPsiSymbolDeclarations(project, symbol, searchScope)
      .findAll();
    final boolean declarationWrite = psiTarget != null && detector != null && detector.isDeclarationWriteAccess(psiTarget);
    for (PsiSymbolDeclaration declaration : declarations) {
      HighlightUsagesHandler.collectHighlightRanges(
        declaration.getDeclaringElement(), declaration.getDeclarationRange(), declarationWrite ? writeRanges : readRanges
      );
    }
    return Couple.of(readRanges, writeRanges);
  }

  @NotNull
  private static Collection<? extends PsiSymbolReference> getReferences(@NotNull Project project,
                                                                        @NotNull SearchScope searchScope,
                                                                        @NotNull Symbol symbol,
                                                                        @Nullable PsiElement psiTarget) {
    if (psiTarget != null) {
      FindUsagesHandler oldHandler = ((FindManagerImpl)FindManager.getInstance(project))
        .getFindUsagesManager()
        .getFindUsagesHandler(psiTarget, true);
      if (oldHandler != null) {
        return oldHandler.findReferencesToHighlight(psiTarget, searchScope);
      }
    }
    return SearchService.getInstance()
      .searchPsiSymbolReferences(project, symbol, searchScope)
      .findAll();
  }

  @Override
  public void doApplyInformationToEditor() {
    final boolean virtSpace = EditorUtil.isCaretInVirtualSpace(myEditor);
    final List<HighlightInfo> infos = virtSpace || isCaretOverCollapsedFoldRegion() ? Collections.emptyList() : getHighlights();
    UpdateHighlightersUtil.setHighlightersToSingleEditor(myProject, myEditor, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());
    doAdditionalCodeBlockHighlighting();
  }

  private boolean isCaretOverCollapsedFoldRegion() {
    return myEditor.getFoldingModel().getCollapsedRegionAtOffset(myEditor.getCaretModel().getOffset()) != null;
  }

  /**
   * Does additional work on code block markers highlighting: <ul>
   * <li>Draws vertical line covering the scope on the gutter by {@link BraceHighlightingHandler#lineMarkFragment(com.intellij.openapi.editor.ex.EditorEx, com.intellij.openapi.editor.Document, int, int, boolean)}</li>
   * <li>Schedules preview of the block start if necessary by {@link BraceHighlightingHandler#showScopeHint(Editor, com.intellij.util.Alarm, int, int, com.intellij.util.IntIntFunction)}</li>
   * </ul>
   *
   * In brace matching case this is done from {@link BraceHighlightingHandler#highlightBraces(com.intellij.openapi.util.TextRange, com.intellij.openapi.util.TextRange, boolean, boolean, com.intellij.openapi.fileTypes.FileType)}
   */
  private void doAdditionalCodeBlockHighlighting() {
    if (myCodeBlockMarkerRanges.size() < 2 || !(myEditor instanceof EditorEx)) {
      return;
    }
    ArrayList<TextRange> markers = new ArrayList<>(myCodeBlockMarkerRanges);
    markers.sort(Segment.BY_START_OFFSET_THEN_END_OFFSET);
    TextRange leftBraceRange = markers.get(0);
    TextRange rightBraceRange = markers.get(markers.size() - 1);
    final int startLine = myEditor.offsetToLogicalPosition(leftBraceRange.getStartOffset()).line;
    final int endLine = myEditor.offsetToLogicalPosition(rightBraceRange.getEndOffset()).line;
    if (endLine - startLine > 0) {
      BraceHighlightingHandler.lineMarkFragment((EditorEx)myEditor, myDocument, startLine, endLine, true);
    }

    BraceHighlightingHandler.showScopeHint(
      myEditor, BraceHighlighter.getAlarm(), leftBraceRange.getStartOffset(), leftBraceRange.getEndOffset(), null);
  }

  private List<HighlightInfo> getHighlights() {
    if (myReadAccessRanges.isEmpty() && myWriteAccessRanges.isEmpty() && myCodeBlockMarkerRanges.isEmpty()) {
      return Collections.emptyList();
    }
    Set<Pair<Object, TextRange>> existingMarkupTooltips = new HashSet<>();
    for (RangeHighlighter highlighter : myEditor.getMarkupModel().getAllHighlighters()) {
      existingMarkupTooltips.add(Pair.create(highlighter.getErrorStripeTooltip(), new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset())));
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

  private HighlightInfo createHighlightInfo(TextRange range, HighlightInfoType type, Set<Pair<Object, TextRange>> existingMarkupTooltips) {
    int start = range.getStartOffset();
    String tooltip = start <= myDocument.getTextLength() ? HighlightHandlerBase.getLineTextErrorStripeTooltip(myDocument, start, false) : null;
    String unescapedTooltip = existingMarkupTooltips.contains(new Pair<Object, TextRange>(tooltip, range)) ? null : tooltip;
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
    if (unescapedTooltip != null) {
      builder.unescapedToolTip(unescapedTooltip);
    }
    return builder.createUnconditionally();
  }

  public static void clearMyHighlights(Document document, Project project) {
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
