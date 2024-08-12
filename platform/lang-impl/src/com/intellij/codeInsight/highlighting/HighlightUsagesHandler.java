// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.IdentifierUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.EditorSearchSession;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class HighlightUsagesHandler extends HighlightHandlerBase {
  public static void invoke(final @NotNull Project project, final @NotNull Editor editor, @Nullable PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final SelectionModel selectionModel = editor.getSelectionModel();
    if (file == null && !selectionModel.hasSelection()) {
      selectionModel.selectWordAtCaret(false);
    }
    if (file == null || selectionModel.hasSelection()) {
      doRangeHighlighting(editor, project);
      return;
    }

    final HighlightUsagesHandlerBase<?> handler = createCustomHandler(editor, file);
    if (handler != null) {
      final String featureId = handler.getFeatureId();

      if (featureId != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(featureId);
      }

      handler.highlightUsages();
      return;
    }

    DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      if (!HighlightUsagesKt.highlightUsages(project, editor, file)) {
        handleNoUsageTargets(file, editor, selectionModel, project);
      }
    });
  }

  private static void handleNoUsageTargets(@NotNull PsiFile file,
                                           @NotNull Editor editor,
                                           @NotNull SelectionModel selectionModel,
                                           @NotNull Project project) {
    if (file.findElementAt(editor.getCaretModel().getOffset()) instanceof PsiWhiteSpace) return;
    selectionModel.selectWordAtCaret(false);
    String selection = selectionModel.getSelectedText();
    if (selection != null) {
      for (int i = 0; i < selection.length(); i++) {
        if (!Character.isJavaIdentifierPart(selection.charAt(i))) {
          selectionModel.removeSelection();
        }
      }
    }

    doRangeHighlighting(editor, project);
    selectionModel.removeSelection();
  }

  public static @Nullable <T extends PsiElement> HighlightUsagesHandlerBase<T> createCustomHandler(@NotNull Editor editor, @NotNull PsiFile file) {
    ThreadingAssertions.assertEventDispatchThread();
    ProperTextRange visibleRange = editor.calculateVisibleRange();
    return createCustomHandler(editor, file, visibleRange);
  }

  /**
   * @see HighlightUsagesHandlerFactory#createHighlightUsagesHandler(Editor, PsiFile, ProperTextRange)
   */
  public static @Nullable <T extends PsiElement> HighlightUsagesHandlerBase<T> createCustomHandler(@NotNull Editor editor,
                                                                                         @NotNull PsiFile file,
                                                                                         @NotNull ProperTextRange visibleRange) {
    DumbService dumbService = DumbService.getInstance(file.getProject());
    for (HighlightUsagesHandlerFactory factory : HighlightUsagesHandlerFactory.EP_NAME.getExtensionList()) {
      if (!dumbService.isUsableInCurrentContext(factory)) continue;
      HighlightUsagesHandlerBase<T> handler = factory.createHighlightUsagesHandler(editor, file, visibleRange);
      if (handler != null && dumbService.isUsableInCurrentContext(handler)) {
        return handler;
      }
    }
    return null;
  }

  private static void doRangeHighlighting(@NotNull Editor editor, @NotNull Project project) {
    if (!editor.getSelectionModel().hasSelection()) return;

    final String text = editor.getSelectionModel().getSelectedText();
    if (text == null) return;

    if (editor instanceof EditorWindow) {
      // highlight selection in the whole editor, not injected fragment only
      editor = ((EditorWindow)editor).getDelegate();
    }

    EditorSearchSession oldSearch = EditorSearchSession.get(editor);
    if (oldSearch != null) {
      if (oldSearch.hasMatches()) {
        String oldText = oldSearch.getTextInField();
        if (!oldSearch.getFindModel().isRegularExpressions()) {
          oldText = StringUtil.escapeToRegexp(oldText);
          oldSearch.getFindModel().setRegularExpressions(true);
        }

        String newText = oldText + '|' + StringUtil.escapeToRegexp(text);
        oldSearch.setTextInField(newText);
        return;
      }
    }

    EditorSearchSession.start(editor, project).getFindModel().setRegularExpressions(false);
  }

  public static final class DoHighlightRunnable implements Runnable {
    private final List<? extends PsiReference> myRefs;
    private final @NotNull Project myProject;
    private final PsiElement myTarget;
    private final Editor myEditor;
    private final PsiFile myFile;
    private final boolean myClearHighlights;

    public DoHighlightRunnable(@NotNull List<? extends PsiReference> refs,
                               @NotNull Project project,
                               @NotNull PsiElement target,
                               @NotNull Editor editor,
                               @NotNull PsiFile file,
                               boolean clearHighlights) {
      myRefs = refs;
      myProject = project;
      myTarget = target;
      myEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
      myFile = file;
      myClearHighlights = clearHighlights;
    }

    @Override
    public void run() {
      highlightReferences(myProject, myTarget, myRefs, myEditor, myFile, myClearHighlights);
      setStatusText(myProject, getElementName(myTarget), myRefs.size(), myClearHighlights);
    }
  }

  public static void highlightReferences(@NotNull Project project,
                                         @NotNull PsiElement element,
                                         @NotNull List<? extends PsiReference> refs,
                                         @NotNull Editor editor,
                                         @NotNull PsiFile file,
                                         boolean clearHighlights) {
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    setupFindModel(project);

    ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(element);

    if (detector != null) {
      List<PsiReference> readRefs = new ArrayList<>();
      List<PsiReference> writeRefs = new ArrayList<>();

      for (PsiReference ref : refs) {
        if (detector.getReferenceAccess(element, ref) == ReadWriteAccessDetector.Access.Read) {
          readRefs.add(ref);
        }
        else {
          writeRefs.add(ref);
        }
      }
      doHighlightRefs(highlightManager, editor, readRefs, EditorColors.SEARCH_RESULT_ATTRIBUTES, clearHighlights);
      doHighlightRefs(highlightManager, editor, writeRefs, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, clearHighlights);
    }
    else {
      doHighlightRefs(highlightManager, editor, refs, EditorColors.SEARCH_RESULT_ATTRIBUTES, clearHighlights);
    }

    TextRange range = getNameIdentifierRange(file, element);
    if (range != null) {
      TextAttributesKey nameAttributes = detector != null && detector.isDeclarationWriteAccess(element)
                                         ? EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
                                         : EditorColors.SEARCH_RESULT_ATTRIBUTES;
      highlightRanges(highlightManager, editor, nameAttributes, clearHighlights, Collections.singletonList(range));
    }
  }

  @ApiStatus.Experimental
  public static void highlightUsages(@NotNull Project project,
                                     @NotNull Editor editor,
                                     @NotNull List<? extends TextRange> readUsages,
                                     @NotNull List<? extends TextRange> writeUsages,
                                     boolean clearHighlights) {
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    setupFindModel(project);
    highlightRanges(highlightManager, editor, EditorColors.SEARCH_RESULT_ATTRIBUTES, clearHighlights, readUsages);
    highlightRanges(highlightManager, editor, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, clearHighlights, writeUsages);
  }

  /**
   * @return range (in the host file) to be highlighted by {@link com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass} for this element
   */
  public static @Nullable TextRange getNameIdentifierRange(@NotNull PsiFile file, @NotNull PsiElement element) {
    InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(file.getProject());
    Pair<PsiElement, TextRange> pair = getNameIdentifierRangeInCurrentRoot(file, element);
    if (pair == null) return null;
    return injectedManager.injectedToHost(pair.getFirst(), pair.getSecond());
  }

  /**
   * @return range (in the current containing file) to be highlighted by {@link com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass} for this element,
   * and the context element for this range
   */
  public static @Nullable Pair<PsiElement, TextRange> getNameIdentifierRangeInCurrentRoot(@NotNull PsiFile file, @NotNull PsiElement element) {
    if (element instanceof PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof PsiDeclaredTarget declaredTarget) {
        final TextRange range = declaredTarget.getNameIdentifierRange();
        if (range != null) {
          if (range.getStartOffset() < 0 || range.getLength() <= 0) {
            return null;
          }
          final PsiElement navElement = declaredTarget.getNavigationElement();
          if (PsiUtilBase.isUnderPsiRoot(file, navElement)) {
            return Pair.create(navElement, range.shiftRight(navElement.getTextRange().getStartOffset()));
          }
        }
      }
    }

    if (!PsiUtilBase.isUnderPsiRoot(file, element)) {
      return null;
    }

    PsiElement identifier = IdentifierUtil.getNameIdentifier(element);
    if (identifier != null && PsiUtilBase.isUnderPsiRoot(file, identifier)) {
      TextRange range = identifier instanceof ExternallyAnnotated
                        ? ((ExternallyAnnotated)identifier).getAnnotationRegion() // the way to skip the id highlighting
                        : identifier.getTextRange();
      return range == null ? null : Pair.create(identifier, range);
    }
    return null;
  }

  public static void highlightRanges(@NotNull HighlightManager highlightManager,
                                     @NotNull Editor editor,
                                     @NotNull TextAttributesKey attributesKey,
                                     boolean clearHighlights,
                                     @NotNull List<? extends TextRange> textRanges) {
    highlightRanges(highlightManager, editor, null, attributesKey, clearHighlights, textRanges);
  }

  private static void highlightRanges(@NotNull HighlightManager highlightManager,
                                     @NotNull Editor editor,
                                     @Nullable TextAttributes attributes,
                                     @Nullable TextAttributesKey attributesKey,
                                     boolean clearHighlights,
                                     @NotNull List<? extends TextRange> textRanges) {
    assert attributes != null || attributesKey != null : "Both attributes and attributesKey are null";

    if (clearHighlights) {
      clearHighlights(editor, highlightManager, textRanges, attributes, attributesKey);
      return;
    }
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    for (TextRange range : textRanges) {
      if (attributes != null) {
        //noinspection deprecation
        highlightManager.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, false, highlighters);
        continue;
      }
      highlightManager.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributesKey, false, highlighters);

    }
    for (RangeHighlighter highlighter : highlighters) {
      String tooltip = getLineTextErrorStripeTooltip(editor.getDocument(), highlighter.getStartOffset(), true);
      highlighter.setErrorStripeTooltip(tooltip);
    }
  }

  public static boolean isClearHighlights(@NotNull Editor editor) {
    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();

    RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(editor.getProject())).getHighlighters(editor);
    int caretOffset = editor.getCaretModel().getOffset();
    for (RangeHighlighter highlighter : highlighters) {
      if (highlighter.getTextRange().grown(1).contains(caretOffset)) {
        return true;
      }
    }
    return false;
  }

  private static void clearHighlights(@NotNull Editor editor,
                                      @NotNull HighlightManager highlightManager,
                                      @NotNull List<? extends TextRange> rangesToHighlight,
                                      @Nullable TextAttributes attributes,
                                      @Nullable TextAttributesKey attributesKey) {
    assert attributes != null || attributesKey != null : "Both attributes and attributesKey are null";

    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();
    RangeHighlighter[] highlighters = ((HighlightManagerImpl)highlightManager).getHighlighters(editor);
    Arrays.sort(highlighters, Comparator.comparingInt(RangeMarker::getStartOffset));
    rangesToHighlight.sort(Comparator.comparingInt(TextRange::getStartOffset));
    int i = 0;
    int j = 0;
    while (i < highlighters.length && j < rangesToHighlight.size()) {
      RangeHighlighter highlighter = highlighters[i];
      TextRange highlighterRange = highlighter.getTextRange();
      TextRange refRange = rangesToHighlight.get(j);
      if (refRange.equals(highlighterRange) &&
          highlighter.getLayer() == HighlighterLayer.SELECTION - 1 &&
          (Objects.equals(attributesKey, highlighter.getTextAttributesKey()) ||
           Objects.equals(attributes, highlighter.getTextAttributes(editor.getColorsScheme())))) {
        highlightManager.removeSegmentHighlighter(editor, highlighter);
        i++;
      }
      else if (refRange.getStartOffset() > highlighterRange.getEndOffset()) {
        i++;
      }
      else if (refRange.getEndOffset() < highlighterRange.getStartOffset()) {
        j++;
      }
      else {
        i++;
        j++;
      }
    }
  }

  private static void doHighlightRefs(@NotNull HighlightManager highlightManager,
                                      @NotNull Editor editor,
                                      @NotNull List<? extends PsiReference> refs,
                                      @NotNull TextAttributesKey attributesKey,
                                      boolean clearHighlights) {
    List<TextRange> textRanges = new ArrayList<>(refs.size());
    for (PsiReference ref : refs) {
      collectHighlightRanges(ref, textRanges);
    }
    highlightRanges(highlightManager, editor, attributesKey, clearHighlights, textRanges);
  }

  // NB don't deprecate this method while PsiSymbolReference is @Experimental
  public static @NotNull List<TextRange> collectRangesToHighlight(@NotNull PsiReference ref, @NotNull List<TextRange> result) {
    collectHighlightRanges(ref, result);
    return result;
  }

  public static void collectHighlightRanges(@NotNull PsiReference ref, @NotNull Collection<? super TextRange> result) {
    for (TextRange relativeRange : ReferenceRange.getRanges(ref)) {
      collectHighlightRanges(ref.getElement(), relativeRange, result);
    }
  }

  public static void collectHighlightRanges(@NotNull PsiElement element,
                                            @NotNull TextRange rangeInElement,
                                            @NotNull Collection<? super TextRange> result) {
    TextRange range = safeCut(element.getTextRange(), rangeInElement);
    if (range.isEmpty()) return;
    result.add(InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, range));
  }

  private static @NotNull TextRange safeCut(@NotNull TextRange range, @NotNull TextRange relative) {
    int start = Math.min(range.getEndOffset(), range.getStartOffset() + relative.getStartOffset());
    int end = Math.min(range.getEndOffset(), range.getStartOffset() + relative.getEndOffset());
    return new TextRange(start, end);
  }

  static void setStatusText(@NotNull Project project, @Nullable String elementName, int refCount, boolean clearHighlights) {
    String message;
    if (clearHighlights) {
      message = "";
    }
    else if (refCount > 0) {
      message = CodeInsightBundle.message(elementName != null ?
                                          "status.bar.highlighted.usages.message" :
                                          "status.bar.highlighted.usages.no.target.message", refCount, elementName, getShortcutText());
    }
    else {
      message = CodeInsightBundle.message(elementName != null ?
                                          "status.bar.highlighted.usages.not.found.message" :
                                          "status.bar.highlighted.usages.not.found.no.target.message", elementName);
    }
    WindowManager.getInstance().getStatusBar(project).setInfo(message);
  }

  private static @NotNull String getElementName(@NotNull PsiElement element) {
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }

  public static @NotNull String getShortcutText() {
    final Shortcut[] shortcuts = ActionManager.getInstance()
      .getAction(IdeActions.ACTION_HIGHLIGHT_USAGES_IN_FILE)
      .getShortcutSet()
      .getShortcuts();
    if (shortcuts.length == 0) {
      return "<no key assigned>";
    }
    return KeymapUtil.getShortcutText(shortcuts[0]);
  }
}
