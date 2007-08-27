package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.EditorSearchComponent;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;

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

    PsiElement target = getTargetElement(editor);
    PsiElement[] targets = null;

    if (target == null) {
      PsiReference ref = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());

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

  private static PsiElement getTargetElement(Editor editor) {
    PsiElement target = TargetElementUtil.findTargetElement(editor,
                                                            TargetElementUtil.ELEMENT_NAME_ACCEPTED |
                                                            TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                            TargetElementUtil.NEW_AS_CONSTRUCTOR |
                                                            TargetElementUtil.LOOKUP_ITEM_ACCEPTED |
                                                            TargetElementUtil.TRY_ACCEPTED |
                                                            TargetElementUtil.CATCH_ACCEPTED |
                                                            TargetElementUtil.THROWS_ACCEPTED |
                                                            TargetElementUtil.THROW_ACCEPTED |
                                                            TargetElementUtil.RETURN_ACCEPTED);

    if (target instanceof PsiCompiledElement) target = ((PsiCompiledElement)target).getMirror();
    return target;
  }

  private static void doRangeHighlighting(Editor editor, Project project) {
    if (!editor.getSelectionModel().hasSelection()) return;

    final EditorSearchComponent header = new EditorSearchComponent(editor, project);
    editor.setHeaderComponent(header);
  }

  private static final Runnable EMPTY_HIGHLIGHT_RUNNABLE = EmptyRunnable.getInstance();

  private static class DoHighlightExitPointsRunnable implements Runnable {
    private final Project myProject;
    private final Editor myEditor;
    private final PsiElement[] myExitStatements;
    private final boolean myClearHighlights;

    public DoHighlightExitPointsRunnable(Project project, Editor editor, PsiElement[] exitStatements, boolean clearHighlights) {
      myProject = project;
      myEditor = editor;
      myExitStatements = exitStatements;
      myClearHighlights = clearHighlights;
    }

    public void run() {
      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      doHighlightElements(highlightManager, myEditor, myExitStatements, attributes, myClearHighlights);

      setupFindModel(myProject);
      String message = CodeInsightBundle.message("status.bar.exit.points.highlighted.message", myExitStatements.length);
      WindowManager.getInstance().getStatusBar(myProject).setInfo(message);
    }
  }

  private static class DoHighlightRunnable implements Runnable {
    private final List<PsiReference> myRefs;
    private final Project myProject;
    private final PsiElement myTarget;
    private final Editor myEditor;
    private final PsiFile myFile;
    private boolean myClearHighlights;

    public DoHighlightRunnable(@NotNull List<PsiReference> refs, @NotNull Project project, @NotNull PsiElement target, Editor editor,
                               PsiFile file, boolean clearHighlights) {
      myRefs = refs;
      myProject = project;
      myTarget = target;
      myEditor = editor;
      myFile = file;
      myClearHighlights = clearHighlights;
    }

    public void run() {
      highlightReferences(myProject, myTarget, myRefs, myEditor, myFile, myClearHighlights);

      setStatusText(myTarget, myRefs.size(), myProject);
    }
  }

  private class ChooseExceptionClassAndDoHighlightRunnable implements Runnable {
    private final PsiClass[] myExceptionClasses;
    private final PsiElement myHighlightInPlace;
    private final Project myProject;
    private final PsiElement myTarget;
    private final Editor myEditor;
    private final PsiFile myFile;
    private JList myList;
    private TypeFilter myTypeFilter = ANY_TYPE;
    private final boolean myClearHighlights;

    public ChooseExceptionClassAndDoHighlightRunnable(PsiClassType[] exceptions, PsiElement highlightInPlace, Project project,
                                                      PsiElement target, Editor editor, PsiFile file, boolean clearHighlights) {
      myClearHighlights = clearHighlights;
      List<PsiClass> classes = new ArrayList<PsiClass>();
      for (PsiClassType exception1 : exceptions) {
        PsiClass exception = exception1.resolve();
        if (exception != null) classes.add(exception);
      }
      myExceptionClasses = classes.toArray(new PsiClass[classes.size()]);
      myHighlightInPlace = highlightInPlace;
      myProject = project;
      myTarget = target;
      myEditor = editor;
      myFile = file;
    }

    public void setTypeFilter(TypeFilter typeFilter) {
      myTypeFilter = typeFilter;
    }

    private void highlightOtherOccurrences(final ArrayList<PsiElement> otherOccurrences) {
      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      EditorColorsManager manager = EditorColorsManager.getInstance();
      TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

      PsiElement[] elements = otherOccurrences.toArray(new PsiElement[otherOccurrences.size()]);
      doHighlightElements(highlightManager, myEditor, elements, attributes, myClearHighlights);
    }

    public void run() {
      final PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
      if (myExceptionClasses.length == 1) {
        List<PsiReference> refs = new ArrayList<PsiReference>();
        final ArrayList<PsiElement> otherOccurrences = new ArrayList<PsiElement>();
        addExceptionThrownPlaces(refs, otherOccurrences, factory.createType(myExceptionClasses[0]), myHighlightInPlace, myTypeFilter);
        new DoHighlightRunnable(refs, myProject, myTarget, myEditor, myFile, myClearHighlights).run();
        highlightOtherOccurrences(otherOccurrences);
      }
      else if (myExceptionClasses.length > 0) {
        PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();

        Arrays.sort(myExceptionClasses, renderer.getComparator());

        Vector<Object> model = new Vector<Object>(Arrays.asList(myExceptionClasses));
        model.insertElementAt(CodeInsightBundle.message("highlight.thrown.exceptions.chooser.all.entry"), 0);

        myList = new JList(model);
        myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myList.setCellRenderer(renderer);

        renderer.installSpeedSearch(myList);

        final Runnable callback = new Runnable() {
          public void run() {
            int idx = myList.getSelectedIndex();
            if (idx < 0) return;
            ArrayList<PsiReference> refs = new ArrayList<PsiReference>();
            final ArrayList<PsiElement> otherOccurrences = new ArrayList<PsiElement>();
            if (idx > 0) {
              addExceptionThrownPlaces(refs, otherOccurrences, factory.createType(myExceptionClasses[idx - 1]),
                                        myHighlightInPlace,
                                        myTypeFilter);
            }
            else {
              for (PsiClass exceptionClass : myExceptionClasses) {
                addExceptionThrownPlaces(refs, otherOccurrences, factory.createType(exceptionClass),
                                          myHighlightInPlace,
                                          myTypeFilter);
              }
            }

            new DoHighlightRunnable(refs, myProject, myTarget, myEditor, myFile, myClearHighlights).run();
            highlightOtherOccurrences(otherOccurrences);
          }
        };

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            new PopupChooserBuilder(myList).
              setTitle(CodeInsightBundle.message("highlight.exceptions.thrown.chooser.title")).
              setItemChoosenCallback(callback).
              createPopup().
              showInBestPositionFor(myEditor);
          }
        });
      }
    }
  }

  private Runnable createHighlightAction(final Project project, PsiFile file, PsiElement[] targets, final Editor editor) {
    if (file instanceof PsiCompiledElement) file = (PsiFile)((PsiCompiledElement)file).getMirror();
    PsiElement target = targets[0];
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    boolean clearHighlights = isClearHighlights(editor, highlightManager);

    if (target instanceof PsiKeyword) {
      if (PsiKeyword.TRY.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiTryStatement)) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
        PsiTryStatement tryStatement = (PsiTryStatement)parent;

        final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(),
                                                                                      tryStatement.getTryBlock());

        return createChoosingRunnable(project, psiClassTypes, tryStatement.getTryBlock(), target, editor, file, ANY_TYPE, clearHighlights);
      }

      if (PsiKeyword.CATCH.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiCatchSection)) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
        PsiTryStatement tryStatement = ((PsiCatchSection)parent).getTryStatement();

        final PsiParameter param = ((PsiCatchSection)parent).getParameter();
        if (param == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();

        final PsiClassType[] allThrownExceptions = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(),
                                                                                            tryStatement.getTryBlock());
        TypeFilter filter = new TypeFilter() {
          public boolean accept(PsiType type) {
            for (PsiParameter parameter : catchBlockParameters) {
              boolean isAssignable = parameter.getType().isAssignableFrom(type);
              if (parameter != param) {
                if (isAssignable) return false;
              }
              else {
                return isAssignable;
              }
            }
            return false;
          }
        };

        ArrayList<PsiClassType> filtered = new ArrayList<PsiClassType>();
        for (PsiClassType type : allThrownExceptions) {
          if (filter.accept(type)) filtered.add(type);
        }

        return createChoosingRunnable(project, filtered.toArray(new PsiClassType[filtered.size()]),
                                      tryStatement.getTryBlock(), target, editor, file, filter, clearHighlights);
      }

      if (PsiKeyword.THROWS.equals(target.getText())) {
        PsiElement parent = target.getParent().getParent();
        if (!(parent instanceof PsiMethod)) return EMPTY_HIGHLIGHT_RUNNABLE;
        PsiMethod method = (PsiMethod)parent;
        if (method.getBody() == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(method.getBody(),
                                                                                      method.getBody());

        return createChoosingRunnable(project, psiClassTypes, method.getBody(), target, editor, file, ANY_TYPE, clearHighlights);
      }

      if (PsiKeyword.RETURN.equals(target.getText()) || PsiKeyword.THROW.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiThrowStatement)) return EMPTY_HIGHLIGHT_RUNNABLE;

        PsiMethod method = PsiTreeUtil.getParentOfType(target, PsiMethod.class);
        if (method == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        PsiCodeBlock body = method.getBody();
        try {
          ControlFlow flow = ControlFlowFactory.getInstance(project).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);

          List<PsiStatement> exitStatements = new ArrayList<PsiStatement>();
          ControlFlowUtil.findExitPointsAndStatements(flow, flow.getStartOffset(body), flow.getEndOffset(body), new IntArrayList(),
                                                      exitStatements,
                                                      new Class[]{PsiReturnStatement.class, PsiBreakStatement.class,
                                                                  PsiContinueStatement.class, PsiThrowStatement.class,
                                                                  PsiExpressionStatement.class});

          if (!exitStatements.contains(parent)) return EMPTY_HIGHLIGHT_RUNNABLE;

          return new DoHighlightExitPointsRunnable(project, editor, exitStatements.toArray(new PsiElement[exitStatements.size()]), clearHighlights);
        }
        catch (AnalysisCanceledException e) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
      }
    }

    // in case of injected file, use host file to highlight all occurences of the target in each injected file
    PsiElement context = file.getContext();
    context = context == null ? file : context.getContainingFile();
    SearchScope searchScope = new LocalSearchScope(context);
    Collection<PsiReference> refs;
    if (target instanceof PsiMethod) {
      refs = MethodReferencesSearch.search((PsiMethod)target, searchScope, true).findAll();
    }
    else {
      refs = ReferencesSearch.search(target, searchScope, false).findAll();
    }

    return new DoHighlightRunnable(new ArrayList<PsiReference>(refs), project, target, editor, file, clearHighlights);
  }

  private Runnable createChoosingRunnable(Project project, final PsiClassType[] psiClassTypes,
                                          PsiElement place, PsiElement target, Editor editor,
                                          PsiFile file, TypeFilter typeFilter, boolean clearHighlights) {
    if (psiClassTypes == null || psiClassTypes.length == 0) return EMPTY_HIGHLIGHT_RUNNABLE;

    final ChooseExceptionClassAndDoHighlightRunnable highlightRunnable =
      new ChooseExceptionClassAndDoHighlightRunnable(psiClassTypes, place, project, target, editor, file, clearHighlights);
    highlightRunnable.setTypeFilter(typeFilter);
    return highlightRunnable;
  }

  private interface TypeFilter {
    boolean accept(PsiType type);
  }

  private static final TypeFilter ANY_TYPE = new TypeFilter() {
    public boolean accept(PsiType type) {
      return true;
    }
  };

  private static void addExceptionThrownPlaces(final List<PsiReference> refs, final List<PsiElement> otherOccurrences, final PsiType type,
                                               final PsiElement block, final TypeFilter typeFilter) {
    if (type instanceof PsiClassType) {
      block.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        public void visitThrowStatement(PsiThrowStatement statement) {
          super.visitThrowStatement(statement);
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(statement, block);
          for (final PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
              PsiExpression psiExpression = statement.getException();
              if (psiExpression instanceof PsiReferenceExpression) {
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression)psiExpression;
                if (!refs.contains(referenceExpression)) refs.add(referenceExpression);
              }
              else if (psiExpression instanceof PsiNewExpression) {
                PsiJavaCodeReferenceElement ref = ((PsiNewExpression)psiExpression).getClassReference();
                if (ref != null && !refs.contains(ref)) refs.add(ref);
              }
              else {
                otherOccurrences.add(statement.getException());
              }
            }
          }
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          PsiReference reference = expression.getMethodExpression().getReference();
          if (reference == null || refs.contains(reference)) return;
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, block);
          for (final PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
              refs.add(reference);
              break;
            }
          }
        }

        public void visitNewExpression(PsiNewExpression expression) {
          super.visitNewExpression(expression);
          PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
          if (classReference == null || refs.contains(classReference)) return;
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, block);
          for (PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
              refs.add(classReference);
              break;
            }
          }
        }
      });
    }
  }

  private static void highlightReferences(@NotNull Project project, @NotNull PsiElement element, @NotNull List<PsiReference> refs, Editor editor,
                                          PsiFile file,
                                          boolean clearHighlights) {

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    TextAttributes writeAttributes = manager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    setupFindModel(project);

    if (element instanceof PsiVariable ||
        element instanceof XmlAttributeValue ||
        element instanceof XmlTag ||
        element instanceof XmlElementDecl
       ) {
      List<PsiReference> readRefs = new ArrayList<PsiReference>();
      List<PsiReference> writeRefs = new ArrayList<PsiReference>();

      for (PsiReference ref : refs) {
        PsiElement refElement = ref.getElement();
        
        if (refElement instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)refElement) ||
            ( refElement instanceof XmlAttributeValue &&
              (!(element instanceof XmlTag) ||
               refElement.getParent().getParent() == element)
            ) ||
            refElement instanceof XmlElementDecl
            )
        {
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
    if (identifier != null && PsiUtil.isUnderPsiRoot(file, identifier)) {
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

  private static void doHighlightElements(HighlightManager highlightManager, Editor editor, PsiElement[] elements, TextAttributes attributes,
                                          boolean clearHighlights) {
    List<TextRange> textRanges = new ArrayList<TextRange>(elements.length);
    for (PsiElement element : elements) {
      textRanges.add(element.getTextRange());
    }
    if (clearHighlights) {
      clearHighlights(editor, highlightManager, textRanges, attributes);
      return;
    }
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    Document document = editor.getDocument();
    highlightManager.addOccurrenceHighlights(editor, elements, attributes, false, highlighters);
    for (int idx = 0; idx < highlighters.size(); idx++) {
      RangeHighlighter highlighter = highlighters.get(idx);
      int offset = elements[idx].getTextRange().getStartOffset();
      setLineTextErrorStripeTooltip(document, offset, highlighter);
    }
  }

  private static boolean isClearHighlights(Editor editor, Object highlightManager) {
    RangeHighlighter[] highlighters = ((HighlightManagerImpl)highlightManager).getHighlighters(editor);
    int caretOffset = editor.getCaretModel().getOffset();
    for (RangeHighlighter highlighter : highlighters) {
      if (new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()).contains(caretOffset)) {
        return true;
      }
    }
    return false;
  }

  private static boolean clearHighlights(Editor editor, HighlightManager highlightManager, List<TextRange> rangesToHighlight, TextAttributes attributes) {
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
        j++;
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
    return true;
  }

  private static void doHighlightRefs(HighlightManager highlightManager, @NotNull Editor editor, @NotNull List<PsiReference> refs,
                                      TextAttributes attributes, boolean clearHighlights) {
    List<TextRange> textRanges = new ArrayList<TextRange>(refs.size());
    for (PsiReference ref : refs) {
      PsiElement element = ref.getElement();
      TextRange rangeInElement = ref.getRangeInElement();
      TextRange range = element.getTextRange().cutOut(rangeInElement);
      textRanges.add(range);
    }
    if (clearHighlights) {
      clearHighlights(editor, highlightManager, textRanges, attributes);
      return;
    }
    ArrayList<RangeHighlighter> outHighlighters = new ArrayList<RangeHighlighter>();
    Document document = editor.getDocument();
    PsiReference[] refArray = refs.toArray(new PsiReference[refs.size()]);
    highlightManager.addOccurrenceHighlights(editor, refArray, attributes, false, outHighlighters);
    for (int idx = 0; idx < outHighlighters.size(); idx++) {
      RangeHighlighter highlighter = outHighlighters.get(idx);
      int offset = refArray[idx].getElement().getTextRange().getStartOffset() + refArray[idx].getRangeInElement().getStartOffset();
      setLineTextErrorStripeTooltip(document, offset, highlighter);
    }
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
        element.getContainingFile() != null) {
      // Quite hacky way to get name identifier. Depends on getTextOffset overriden properly.
      final PsiElement potentialIdentifier = element.findElementAt(element.getTextOffset() - element.getTextRange().getStartOffset());
      if (potentialIdentifier != null && Comparing.equal(potentialIdentifier.getText(), ((PsiNamedElement)element).getName(), false)) {
        return potentialIdentifier;
      }
    }

    return null;
  }

  private static void setStatusText(PsiElement element, int refCount, Project project) {
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
    if (element instanceof PsiKeyword &&
        (PsiKeyword.TRY.equals(element.getText()) || PsiKeyword.CATCH.equals(element.getText()) ||
         PsiKeyword.THROWS.equals(element.getText()))) {
      elementName = LangBundle.message("java.terms.exception");
    }

    String message;
    if (refCount > 0) {
      message = CodeInsightBundle.message(elementName != null ?
                                        "status.bar.highlighted.usages.message" :
                                        "status.bar.highlighted.usages.no.target.message", refCount, elementName);
    }
    else {
      message = CodeInsightBundle.message(elementName != null ?
                                          "status.bar.highlighted.usages.not.found.message" :
                                          "status.bar.highlighted.usages.not.found.no.target.message", elementName);
    }

    WindowManager.getInstance().getStatusBar(project).setInfo(message);
  }
}