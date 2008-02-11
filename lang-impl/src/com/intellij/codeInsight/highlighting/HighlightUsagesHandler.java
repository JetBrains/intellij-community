package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.find.EditorSearchComponent;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LangBundle;
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
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlElementDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class HighlightUsagesHandler extends HighlightHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.highlighting.HighlightUsagesHandler");

  public void invoke(@NotNull Project project, @NotNull Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    SelectionModel selectionModel = editor.getSelectionModel();
    if (file == null && !selectionModel.hasSelection()) {
      selectionModel.selectWordAtCaret(false);
    }
    if (file == null || selectionModel.hasSelection()) {
      doRangeHighlighting(editor, project);
      return;
    }

    for(HighlightUsagesHandlerDelegate delegate: Extensions.getExtensions(HighlightUsagesHandlerDelegate.EP_NAME)) {
      if (delegate.highlightUsages(editor, file)) return;
    }

    PsiElement target = getTargetElement(editor, file);
    PsiElement[] targets = null;

    if (target == null) {
      PsiReference ref = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());

      if (ref instanceof PsiPolyVariantReference) {
        ResolveResult[] results = ((PsiPolyVariantReference)ref).multiResolve(false);

        if (results.length > 0) {
          targets = new PsiElement[results.length];
          for (int i = 0; i < results.length; ++i) {
            targets[i] = results[i].getElement();
          }
        }
      }
    }
    else {
      targets = new PsiElement[] {target};
    }

    if (targets == null) {
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

    createHighlightAction(project, file, targets, editor).run();
  }

  @Nullable
  private static PsiElement getTargetElement(Editor editor, PsiFile file) {
    PsiElement target = TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase
      .ELEMENT_NAME_ACCEPTED | TargetElementUtilBase
      .REFERENCED_ELEMENT_ACCEPTED |
                                                            TargetElementUtil
                                                              .NEW_AS_CONSTRUCTOR | TargetElementUtilBase
      .LOOKUP_ITEM_ACCEPTED);

    if (target == null) {
      int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
      PsiElement element = file.findElementAt(offset);
      if (element == null) return null;
    }

    if (target instanceof PsiCompiledElement) target = ((PsiCompiledElement)target).getMirror();
    return target;
  }

  private static void doRangeHighlighting(Editor editor, Project project) {
    if (!editor.getSelectionModel().hasSelection()) return;

    final String text = editor.getSelectionModel().getSelectedText();
    if (text == null || text.indexOf('\n') >= 0) return;

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

  private static final Runnable EMPTY_HIGHLIGHT_RUNNABLE = EmptyRunnable.getInstance();

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

  private static Runnable createHighlightAction(final Project project, PsiFile file, PsiElement[] targets, final Editor editor) {
    if (file instanceof PsiCompiledElement) file = (PsiFile)((PsiCompiledElement)file).getMirror();
    PsiElement target = targets[0];
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    boolean clearHighlights = isClearHighlights(editor, highlightManager);

    // in case of injected file, use host file to highlight all occurences of the target in each injected file
    PsiFile context = InjectedLanguageUtil.getTopLevelFile(file);
    SearchScope searchScope = new LocalSearchScope(context);
    Collection<PsiReference> refs;
    if (target instanceof PsiMethod) {
      refs = MethodReferencesSearch.search((PsiMethod)target, searchScope, true).findAll();
    }
    else {
      refs = ReferencesSearch.search(target, searchScope, false).findAll();
    }

    return new DoHighlightRunnable(new ArrayList<PsiReference>(refs), project, target, editor, context, clearHighlights);
  }

  public static void highlightOtherOccurrences(final List<PsiElement> otherOccurrences, Project myProject, Editor myEditor, boolean clearHighlights) {
    HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

    PsiElement[] elements = otherOccurrences.toArray(new PsiElement[otherOccurrences.size()]);
    doHighlightElements(highlightManager, myEditor, elements, attributes, clearHighlights);
  }

  public static void highlightReferences(@NotNull Project project, @NotNull PsiElement element, @NotNull List<PsiReference> refs, Editor editor,
                                         PsiFile file, boolean clearHighlights) {

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    TextAttributes writeAttributes = manager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    setupFindModel(project);

    ReadWriteAccessDetector detector = null;
    for(ReadWriteAccessDetector accessDetector: Extensions.getExtensions(ReadWriteAccessDetector.EP_NAME)) {
      if (accessDetector.isReadWriteAccessible(element)) {
        detector = accessDetector;
        break;
      }
    }

    if (detector != null) {
      List<PsiReference> readRefs = new ArrayList<PsiReference>();
      List<PsiReference> writeRefs = new ArrayList<PsiReference>();

      for (PsiReference ref : refs) {
        if (detector.isWriteAccess(element, ref)) {
          writeRefs.add(ref);
        }
        else {
          readRefs.add(ref);
        }
      }
      doHighlightRefs(highlightManager, editor, readRefs, attributes, clearHighlights);
      doHighlightRefs(highlightManager, editor, writeRefs, writeAttributes, clearHighlights);
    }
    else {
      doHighlightRefs(highlightManager, editor, refs, attributes, clearHighlights);
    }

    PsiElement identifier = getNameIdentifier(element);
    if (identifier != null && PsiUtilBase.isUnderPsiRoot(file, identifier)) {
      TextAttributes nameAttributes = attributes;
      if (element instanceof PsiVariable && ((PsiVariable)element).getInitializer() != null) {
        nameAttributes = writeAttributes;
      }
      doHighlightElements(highlightManager, editor, new PsiElement[]{identifier}, nameAttributes, clearHighlights);
    }
    else if (element instanceof PsiKeyword) { //try, catch, throws.
      doHighlightElements(highlightManager, editor, new PsiElement[]{element}, attributes, clearHighlights);
    }
  }

  public static void doHighlightElements(HighlightManager highlightManager, Editor editor, PsiElement[] elements, TextAttributes attributes,
                                          boolean clearHighlights) {
    List<TextRange> textRanges = new ArrayList<TextRange>(elements.length);
    for (PsiElement element : elements) {
      TextRange range = element.getTextRange();
      // injection occurs
      range = InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, range);
      textRanges.add(range);
    }
    highlightRanges(highlightManager, editor, attributes, clearHighlights, textRanges);
  }

  private static void highlightRanges(HighlightManager highlightManager, Editor editor, TextAttributes attributes,
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

  public static boolean isClearHighlights(Editor editor, Object highlightManager) {
    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();
    
    RangeHighlighter[] highlighters = ((HighlightManagerImpl)highlightManager).getHighlighters(editor);
    int caretOffset = editor.getCaretModel().getOffset();
    for (RangeHighlighter highlighter : highlighters) {
      if (new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()).grown(1).contains(caretOffset)) {
        return true;
      }
    }
    return false;
  }

  private static void clearHighlights(Editor editor, HighlightManager highlightManager, List<TextRange> rangesToHighlight, TextAttributes attributes) {
    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();
    RangeHighlighter[] highlighters = ((HighlightManagerImpl)highlightManager).getHighlighters(editor);
    Arrays.sort(highlighters, new Comparator<RangeHighlighter>(){
      public int compare(RangeHighlighter o1, RangeHighlighter o2) {
        return o1.getStartOffset() - o2.getStartOffset();
      }
    });
    Collections.sort(rangesToHighlight, new Comparator<TextRange>(){
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
      if (refRange.equals(highlighterRange) && attributes.equals(highlighter.getTextAttributes()) && highlighter.getLayer() == HighlighterLayer.SELECTION - 1) {
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
      PsiElement element = ref.getElement();
      TextRange rangeInElement = ref.getRangeInElement();
      TextRange range = element.getTextRange().cutOut(rangeInElement);
      // injection occurs
      range = InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, range);
      textRanges.add(range);
    }
    highlightRanges(highlightManager, editor, attributes, clearHighlights, textRanges);
  }

  public static PsiElement getNameIdentifier(@NotNull PsiElement element) {
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getNameIdentifier();
    }
    if (element instanceof PsiMethod) {
      return ((PsiMethod)element).getNameIdentifier();
    }
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getNameIdentifier();
    }

    if (element.isPhysical() &&
        element instanceof PsiNamedElement &&
        !(element instanceof XmlElementDecl) &&
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
    String elementName = null;
    if (element instanceof PsiClass) {
      elementName = ((PsiClass)element).getQualifiedName();
      if (elementName == null) {
        elementName = ((PsiClass)element).getName();
      }
      elementName = (((PsiClass)element).isInterface() ?
                     LangBundle.message("java.terms.interface") :
                     LangBundle.message("java.terms.class")) + " " + elementName;
    }
    else if (element instanceof PsiMethod) {
      elementName = PsiFormatUtil.formatMethod((PsiMethod)element,
                                               PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS |
                                                                     PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                               PsiFormatUtil.SHOW_TYPE);
      elementName = LangBundle.message("java.terms.method") + " " + elementName;
    }
    else if (element instanceof PsiVariable) {
      elementName = PsiFormatUtil.formatVariable((PsiVariable)element,
                                                 PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                                 PsiSubstitutor.EMPTY);
      if (element instanceof PsiField) {
        elementName = LangBundle.message("java.terms.field") + " " + elementName;
      }
      else if (element instanceof PsiParameter) {
        elementName = LangBundle.message("java.terms.parameter") + " " + elementName;
      }
      else {
        elementName = LangBundle.message("java.terms.variable") + " " + elementName;
      }
    }
    else if (element instanceof PsiPackage) {
      elementName = ((PsiPackage)element).getQualifiedName();
      elementName = LangBundle.message("java.terms.package") + " " + elementName;
    }
    return elementName;
  }

  public static String getShortcutText() {
    Shortcut shortcut = ActionManager.getInstance().getAction(IdeActions.ACTION_HIGHLIGHT_USAGES_IN_FILE).getShortcutSet().getShortcuts()[0];
    return KeymapUtil.getShortcutText(shortcut);
  }
}
