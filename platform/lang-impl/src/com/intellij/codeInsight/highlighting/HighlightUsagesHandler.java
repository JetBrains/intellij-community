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

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.find.EditorSearchComponent;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class HighlightUsagesHandler extends HighlightHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.highlighting.HighlightUsagesHandler");

  public static void invoke(@NotNull Project project, @NotNull Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    SelectionModel selectionModel = editor.getSelectionModel();
    if (file == null && !selectionModel.hasSelection()) {
      selectionModel.selectWordAtCaret(false);
    }
    if (file == null || selectionModel.hasSelection()) {
      doRangeHighlighting(editor, project);
      return;
    }

    final HighlightUsagesHandlerBase handler = createCustomHandler(editor, file);
    if (handler != null) {
      handler.highlightUsages();
      return;
    }

    UsageTarget[] usageTargets = UsageTargetUtil.findUsageTargets(editor, file);

    if (usageTargets == null) {
      PsiElement targetElement = getTargetElement(editor, file);
      if (targetElement != null) {
        usageTargets = new UsageTarget[]{new PsiElement2UsageTargetAdapter(targetElement)};
      }
    }

    if (usageTargets == null) {
      PsiReference ref = TargetElementUtilBase.findReference(editor);

      if (ref instanceof PsiPolyVariantReference) {
        ResolveResult[] results = ((PsiPolyVariantReference)ref).multiResolve(false);

        if (results.length > 0) {
          usageTargets = new UsageTarget[results.length];
          for (int i = 0; i < results.length; ++i) {
            usageTargets[i] = new PsiElement2UsageTargetAdapter(results[i].getElement());
          }
        }
      }
    }

    if (usageTargets == null) {
      if (file.findElementAt(editor.getCaretModel().getOffset()) instanceof PsiWhiteSpace) return;
      selectionModel.selectWordAtCaret(false);
      String selection = selectionModel.getSelectedText();
      LOG.assertTrue(selection != null);
      for (int i = 0; i < selection.length(); i++) {
        if (!Character.isJavaIdentifierPart(selection.charAt(i))) {
          selectionModel.removeSelection();
          return;
        }
      }

      doRangeHighlighting(editor, project);
      selectionModel.removeSelection();
      return;
    }

    boolean clearHighlights = isClearHighlights(editor);
    for (UsageTarget target : usageTargets) {
      target.highlightUsages(file, editor, clearHighlights);
    }
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
    PsiElement target = TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase.getInstance().getReferenceSearchFlags());

    if (target == null) {
      int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
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

    final JComponent oldHeader = editor.getHeaderComponent();
    if (oldHeader instanceof EditorSearchComponent) {
      final EditorSearchComponent oldSearch = (EditorSearchComponent)oldHeader;
      if (oldSearch.hasMatches()) {
        String oldText = oldSearch.getTextInField();
        if (!oldSearch.isRegexp()) {
          oldText = StringUtil.escapeToRegexp(oldText);
          oldSearch.setRegexp(true);
        }

        String newText = oldText + '|' + StringUtil.escapeToRegexp(text);
        oldSearch.setTextInField(newText);
        return;
      }
    }

    final EditorSearchComponent header = new EditorSearchComponent(editor, project);
    editor.setHeaderComponent(header);
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

    public void run() {
      highlightReferences(myProject, myTarget, myRefs, myEditor, myFile, myClearHighlights);
      setStatusText(myProject, getElementName(myTarget), myRefs.size(), myClearHighlights);
    }
  }

  public static void highlightOtherOccurrences(final List<PsiElement> otherOccurrences, Editor editor, boolean clearHighlights) {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

    PsiElement[] elements = otherOccurrences.toArray(new PsiElement[otherOccurrences.size()]);
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
      List<PsiReference> readRefs = new ArrayList<PsiReference>();
      List<PsiReference> writeRefs = new ArrayList<PsiReference>();

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

    PsiElement identifier = getNameIdentifier(element);
    if (identifier != null && PsiUtilBase.isUnderPsiRoot(file, identifier)) {
      return injectedManager.injectedToHost(identifier, identifier.getTextRange());
    }
    return null;
  }

  public static void doHighlightElements(Editor editor, PsiElement[] elements, TextAttributes attributes, boolean clearHighlights) {
    HighlightManager highlightManager = HighlightManager.getInstance(editor.getProject());
    List<TextRange> textRanges = new ArrayList<TextRange>(elements.length);
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
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    for (TextRange range : textRanges) {
      highlightManager.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, false, highlighters);
    }
    for (RangeHighlighter highlighter : highlighters) {
      setLineTextErrorStripeTooltip(highlighter);
    }
  }

  public static boolean isClearHighlights(Editor editor) {
    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();

    RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(editor.getProject())).getHighlighters(editor);
    int caretOffset = editor.getCaretModel().getOffset();
    for (RangeHighlighter highlighter : highlighters) {
      if (new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()).grown(1).contains(caretOffset)) {
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
    Arrays.sort(highlighters, new Comparator<RangeHighlighter>() {
      public int compare(RangeHighlighter o1, RangeHighlighter o2) {
        return o1.getStartOffset() - o2.getStartOffset();
      }
    });
    Collections.sort(rangesToHighlight, new Comparator<TextRange>() {
      public int compare(TextRange o1, TextRange o2) {
        return o1.getStartOffset() - o2.getStartOffset();
      }
    });
    int i = 0;
    int j = 0;
    while (i < highlighters.length && j < rangesToHighlight.size()) {
      RangeHighlighter highlighter = highlighters[i];
      TextRange highlighterRange = new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset());
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
    List<TextRange> textRanges = new ArrayList<TextRange>(refs.size());
    for (PsiReference ref : refs) {
      textRanges.addAll(getRangesToHighlight(ref));
    }
    highlightRanges(highlightManager, editor, attributes, clearHighlights, textRanges);
  }

  public static List<TextRange> getRangesToHighlight(final PsiReference ref) {
    final List<TextRange> relativeRanges = ReferenceRange.getRanges(ref);
    List<TextRange> answer = new ArrayList<TextRange>(relativeRanges.size());
    for (TextRange relativeRange : relativeRanges) {
      PsiElement element = ref.getElement();
      TextRange range = element.getTextRange().cutOut(relativeRange);
      // injection occurs
      answer.add(InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, range));

    }
    return answer;
  }

  @Nullable
  public static PsiElement getNameIdentifier(@NotNull PsiElement element) {
    if (element instanceof PsiNameIdentifierOwner) {
      return ((PsiNameIdentifierOwner)element).getNameIdentifier();
    }

    if (element.isPhysical() &&
        element instanceof PsiNamedElement &&
        element.getContainingFile() != null &&
        element.getTextRange() != null) {
      // Quite hacky way to get name identifier. Depends on getTextOffset overriden properly.
      final PsiElement potentialIdentifier = element.findElementAt(element.getTextOffset() - element.getTextRange().getStartOffset());
      if (potentialIdentifier != null && Comparing.equal(potentialIdentifier.getText(), ((PsiNamedElement)element).getName(), false)) {
        return potentialIdentifier;
      }
    }

    return null;
  }

  public static void setStatusText(Project project, final String elementName, int refCount, boolean clearHighlights) {
    String message;
    if (clearHighlights) {
      message = "";
    }
    else {
      if (refCount > 0) {
        message = CodeInsightBundle.message(elementName != null ?
                                            "status.bar.highlighted.usages.message" :
                                            "status.bar.highlighted.usages.no.target.message", refCount, elementName, getShortcutText());
      }
      else {
        message = CodeInsightBundle.message(elementName != null ?
                                            "status.bar.highlighted.usages.not.found.message" :
                                            "status.bar.highlighted.usages.not.found.no.target.message", elementName);
      }
    }
    WindowManager.getInstance().getStatusBar(project).setInfo(message);
  }

  private static String getElementName(final PsiElement element) {
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }

  public static String getShortcutText() {
    Shortcut shortcut = ActionManager.getInstance()
      .getAction(IdeActions.ACTION_HIGHLIGHT_USAGES_IN_FILE)
      .getShortcutSet()
      .getShortcuts()[0];
    return KeymapUtil.getShortcutText(shortcut);
  }
}
