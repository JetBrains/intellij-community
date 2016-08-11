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

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.impl.IdentifierUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.EditorSearchSession;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class HighlightUsagesHandler extends HighlightHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.highlighting.HighlightUsagesHandler");

  public static void invoke(@NotNull final Project project, @NotNull final Editor editor, final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final SelectionModel selectionModel = editor.getSelectionModel();
    if (file == null && !selectionModel.hasSelection()) {
      selectionModel.selectWordAtCaret(false);
    }
    if (file == null || selectionModel.hasSelection()) {
      doRangeHighlighting(editor, project);
      return;
    }

    final HighlightUsagesHandlerBase handler = createCustomHandler(editor, file);
    if (handler != null) {
      final String featureId = handler.getFeatureId();

      if (featureId != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(featureId);
      }

      handler.highlightUsages();
      return;
    }

    DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      UsageTarget[] usageTargets = getUsageTargets(editor, file);
      if (usageTargets == null) {
        handleNoUsageTargets(file, editor, selectionModel, project);
        return;
      }

      boolean clearHighlights = isClearHighlights(editor);
      for (UsageTarget target : usageTargets) {
        target.highlightUsages(file, editor, clearHighlights);
      }
    });
  }

  @Nullable
  private static UsageTarget[] getUsageTargets(@NotNull Editor editor, PsiFile file) {
    UsageTarget[] usageTargets = UsageTargetUtil.findUsageTargets(editor, file);

    if (usageTargets == null) {
      PsiElement targetElement = getTargetElement(editor, file);
      if (targetElement != null && targetElement != file) {
        if (!(targetElement instanceof NavigationItem)) {
          targetElement = targetElement.getNavigationElement();
        }
        if (targetElement instanceof NavigationItem) {
          usageTargets = new UsageTarget[]{new PsiElement2UsageTargetAdapter(targetElement)};
        }
      }
    }

    if (usageTargets == null) {
      PsiReference ref = TargetElementUtil.findReference(editor);

      if (ref instanceof PsiPolyVariantReference) {
        ResolveResult[] results = ((PsiPolyVariantReference)ref).multiResolve(false);

        if (results.length > 0) {
          usageTargets = ContainerUtil.mapNotNull(results, result -> {
            PsiElement element = result.getElement();
            return element == null ? null : new PsiElement2UsageTargetAdapter(element);
          }, UsageTarget.EMPTY_ARRAY);
        }
      }
    }
    return usageTargets;
  }

  private static void handleNoUsageTargets(PsiFile file,
                                           @NotNull Editor editor,
                                           SelectionModel selectionModel,
                                           @NotNull Project project) {
    if (file.findElementAt(editor.getCaretModel().getOffset()) instanceof PsiWhiteSpace) return;
    selectionModel.selectWordAtCaret(false);
    String selection = selectionModel.getSelectedText();
    LOG.assertTrue(selection != null);
    for (int i = 0; i < selection.length(); i++) {
      if (!Character.isJavaIdentifierPart(selection.charAt(i))) {
        selectionModel.removeSelection();
      }
    }

    doRangeHighlighting(editor, project);
    selectionModel.removeSelection();
  }

  @Nullable
  public static HighlightUsagesHandlerBase createCustomHandler(final Editor editor, final PsiFile file) {
    for (HighlightUsagesHandlerFactory factory : Extensions.getExtensions(HighlightUsagesHandlerFactory.EP_NAME)) {
      final HighlightUsagesHandlerBase handler = factory.createHighlightUsagesHandler(editor, file);
      if (handler != null) {
        return handler;
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement getTargetElement(Editor editor, PsiFile file) {
    PsiElement target = TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getReferenceSearchFlags());

    if (target == null) {
      int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
      PsiElement element = file.findElementAt(offset);
      if (element == null) return null;
    }

    return target;
  }

  private static void doRangeHighlighting(Editor editor, Project project) {
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

  public static class DoHighlightRunnable implements Runnable {
    private final List<PsiReference> myRefs;
    private final Project myProject;
    private final PsiElement myTarget;
    private final Editor myEditor;
    private final PsiFile myFile;
    private final boolean myClearHighlights;

    public DoHighlightRunnable(@NotNull List<PsiReference> refs, @NotNull Project project, @NotNull PsiElement target, Editor editor,
                               PsiFile file, boolean clearHighlights) {
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

  public static void highlightOtherOccurrences(final List<PsiElement> otherOccurrences, Editor editor, boolean clearHighlights) {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

    PsiElement[] elements = PsiUtilCore.toPsiElementArray(otherOccurrences);
    doHighlightElements(editor, elements, attributes, clearHighlights);
  }

  public static void highlightReferences(@NotNull Project project,
                                         @NotNull PsiElement element,
                                         @NotNull List<PsiReference> refs,
                                         Editor editor,
                                         PsiFile file,
                                         boolean clearHighlights) {

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    TextAttributes writeAttributes = manager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

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
      doHighlightRefs(highlightManager, editor, readRefs, attributes, clearHighlights);
      doHighlightRefs(highlightManager, editor, writeRefs, writeAttributes, clearHighlights);
    }
    else {
      doHighlightRefs(highlightManager, editor, refs, attributes, clearHighlights);
    }

    TextRange range = getNameIdentifierRange(file, element);
    if (range != null) {
      TextAttributes nameAttributes = attributes;
      if (detector != null && detector.isDeclarationWriteAccess(element)) {
        nameAttributes = writeAttributes;
      }
      highlightRanges(highlightManager, editor, nameAttributes, clearHighlights, Arrays.asList(range));
    }
  }

  @Nullable
  public static TextRange getNameIdentifierRange(PsiFile file, PsiElement element) {
    final InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(element.getProject());
    if (element instanceof PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof PsiDeclaredTarget) {
        final PsiDeclaredTarget declaredTarget = (PsiDeclaredTarget)target;
        final TextRange range = declaredTarget.getNameIdentifierRange();
        if (range != null) {
          if (range.getStartOffset() < 0 || range.getLength() <= 0) {
            return null;
          }
          final PsiElement navElement = declaredTarget.getNavigationElement();
          if (PsiUtilBase.isUnderPsiRoot(file, navElement)) {
            return injectedManager.injectedToHost(navElement, range.shiftRight(navElement.getTextRange().getStartOffset()));
          }
        }
      }
    }

    if (!PsiUtilBase.isUnderPsiRoot(file, element)) {
      return null;
    }

    PsiElement identifier = IdentifierUtil.getNameIdentifier(element);
    if (identifier != null && PsiUtilBase.isUnderPsiRoot(file, identifier)) {
      return injectedManager.injectedToHost(identifier, identifier.getTextRange());
    }
    return null;
  }

  public static void doHighlightElements(Editor editor, PsiElement[] elements, TextAttributes attributes, boolean clearHighlights) {
    HighlightManager highlightManager = HighlightManager.getInstance(editor.getProject());
    List<TextRange> textRanges = new ArrayList<>(elements.length);
    for (PsiElement element : elements) {
      TextRange range = element.getTextRange();
      // injection occurs
      range = InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, range);
      textRanges.add(range);
    }
    highlightRanges(highlightManager, editor, attributes, clearHighlights, textRanges);
  }

  public static void highlightRanges(HighlightManager highlightManager, Editor editor, TextAttributes attributes,
                                     boolean clearHighlights,
                                     List<TextRange> textRanges) {
    if (clearHighlights) {
      clearHighlights(editor, highlightManager, textRanges, attributes);
      return;
    }
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    for (TextRange range : textRanges) {
      highlightManager.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, false, highlighters);
    }
    for (RangeHighlighter highlighter : highlighters) {
      String tooltip = getLineTextErrorStripeTooltip(editor.getDocument(), highlighter.getStartOffset(), true);
      highlighter.setErrorStripeTooltip(tooltip);
    }
  }

  public static boolean isClearHighlights(Editor editor) {
    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();

    RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(editor.getProject())).getHighlighters(editor);
    int caretOffset = editor.getCaretModel().getOffset();
    for (RangeHighlighter highlighter : highlighters) {
      if (TextRange.create(highlighter).grown(1).contains(caretOffset)) {
        return true;
      }
    }
    return false;
  }

  private static void clearHighlights(Editor editor,
                                      HighlightManager highlightManager,
                                      List<TextRange> rangesToHighlight,
                                      TextAttributes attributes) {
    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();
    RangeHighlighter[] highlighters = ((HighlightManagerImpl)highlightManager).getHighlighters(editor);
    Arrays.sort(highlighters, (o1, o2) -> o1.getStartOffset() - o2.getStartOffset());
    Collections.sort(rangesToHighlight, (o1, o2) -> o1.getStartOffset() - o2.getStartOffset());
    int i = 0;
    int j = 0;
    while (i < highlighters.length && j < rangesToHighlight.size()) {
      RangeHighlighter highlighter = highlighters[i];
      TextRange highlighterRange = TextRange.create(highlighter);
      TextRange refRange = rangesToHighlight.get(j);
      if (refRange.equals(highlighterRange) && attributes.equals(highlighter.getTextAttributes()) &&
          highlighter.getLayer() == HighlighterLayer.SELECTION - 1) {
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

  private static void doHighlightRefs(HighlightManager highlightManager, @NotNull Editor editor, @NotNull List<PsiReference> refs,
                                      TextAttributes attributes, boolean clearHighlights) {
    List<TextRange> textRanges = new ArrayList<>(refs.size());
    for (PsiReference ref : refs) {
      collectRangesToHighlight(ref, textRanges);
    }
    highlightRanges(highlightManager, editor, attributes, clearHighlights, textRanges);
  }

  /**
   * @deprecated Use {@link #collectRangesToHighlight}
   */
  @SuppressWarnings("unused")
  @NotNull
  @Deprecated
  public static List<TextRange> getRangesToHighlight(@NotNull PsiReference ref) {
    return collectRangesToHighlight(ref, new ArrayList<>());
  }

  @NotNull
  public static List<TextRange> collectRangesToHighlight(@NotNull PsiReference ref, @NotNull List<TextRange> result) {
    for (TextRange relativeRange : ReferenceRange.getRanges(ref)) {
      PsiElement element = ref.getElement();
      TextRange range = safeCut(element.getTextRange(), relativeRange);
      // injection occurs
      result.add(InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, range));
    }
    return result;
  }

  private static TextRange safeCut(TextRange range, TextRange relative) {
    int start = Math.min(range.getEndOffset(), range.getStartOffset() + relative.getStartOffset());
    int end = Math.min(range.getEndOffset(), range.getStartOffset() + relative.getEndOffset());
    return new TextRange(start, end);
  }

  public static void setStatusText(Project project, final String elementName, int refCount, boolean clearHighlights) {
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

  private static String getElementName(final PsiElement element) {
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }

  public static String getShortcutText() {
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
