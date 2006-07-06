package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.EmptyRunnable;
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
import com.intellij.util.containers.IntArrayList;

import javax.swing.*;
import java.util.*;

public class HighlightUsagesHandler extends HighlightHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.highlighting.HighlightUsagesHandler");

  public void invoke(Project project, Editor editor, PsiFile file) {
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
          for(int i = 0; i < results.length; ++ i) targets[i] = results[i].getElement();
        }
      }
    } else {
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

  protected static PsiElement getTargetElement(Editor editor) {
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

    String text = editor.getSelectionModel().getSelectedText();
    FindManager findManager = FindManager.getInstance(project);
    FindModel model = new FindModel();
    model.setCaseSensitive(true);
    model.setFromCursor(false);
    model.setStringToFind(text);
    model.setSearchHighlighters(true);
    int offset = 0;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
    int count = 0;
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    while (true) {
      FindResult result = findManager.findString(editor.getDocument().getCharsSequence(), offset, model);
      if (!result.isStringFound()) break;
      highlightManager.addRangeHighlight(editor, result.getStartOffset(), result.getEndOffset(), attributes, false, highlighters);
      offset = result.getEndOffset();
      count++;
    }
    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setErrorStripeTooltip(text);
    }
    findManager.setFindWasPerformed();
    findManager.setFindNextModel(model);

    WindowManager.getInstance().getStatusBar(project).
      setInfo(CodeInsightBundle.message("status.bar.highlighted.occurences.message", count, model.getStringToFind()));
  }

  private static final Runnable EMPTY_HIGHLIGHT_RUNNABLE = EmptyRunnable.getInstance();

  private static class DoHighlightExitPointsRunnable implements Runnable {
    private Project myProject;
    private Editor myEditor;
    private PsiElement[] myExitStatements;

    public DoHighlightExitPointsRunnable(Project project, Editor editor, PsiElement[] exitStatements) {
      myProject = project;
      myEditor = editor;
      myExitStatements = exitStatements;
    }

    public void run() {
      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      doHighlightElements(HighlightManager.getInstance(myProject), myEditor, myExitStatements, attributes);

      setupFindModel(myProject);
      String message = CodeInsightBundle.message("status.bar.exit.points.highlighted.message", myExitStatements.length);
      WindowManager.getInstance().getStatusBar(myProject).setInfo(message);
    }
  }

  private static class DoHighlightRunnable implements Runnable {
    private Collection<PsiReference> myRefs;
    private Project myProject;
    private PsiElement myTarget;
    private Editor myEditor;
    private PsiFile myFile;

    public DoHighlightRunnable(Collection<PsiReference> refs, Project project, PsiElement target, Editor editor, PsiFile file) {
      myRefs = refs;
      myProject = project;
      myTarget = target;
      myEditor = editor;
      myFile = file;
    }

    public void run() {
      highlightReferences(myProject, myTarget, myRefs, myEditor, myFile);

      setStatusText(myTarget, myRefs.size(), myProject);

      FindUsagesOptions options = new FindUsagesOptions(myProject);
      options.isUsages = true;
      options.isReadAccess = true;
      options.isWriteAccess = true;
      //FindManager.getInstance(myProject).setLastSearchOperation(myTarget, options);
    }
  }

  private class ChooseExceptionClassAndDoHighlightRunnable implements Runnable {
    private PsiClass[] myExceptionClasses;
    private PsiElement myHighlightInPlace;
    private Project myProject;
    private PsiElement myTarget;
    private Editor myEditor;
    private PsiFile myFile;
    private JList myList;
    private TypeFilter myTypeFilter = ANY_TYPE;

    public ChooseExceptionClassAndDoHighlightRunnable(PsiClassType[] exceptions, PsiElement highlightInPlace, Project project,
                                                      PsiElement target, Editor editor, PsiFile file) {
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

    public void run() {
      final PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
      if (myExceptionClasses.length == 1) {
        ArrayList<PsiReference> refs = new ArrayList<PsiReference>();
        findExceptionThrownPlaces(refs, factory.createType(myExceptionClasses[0]), myHighlightInPlace, myTypeFilter);
        new DoHighlightRunnable(refs, myProject, myTarget,
                                myEditor, myFile).run();
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
            if (idx > 0) {
              findExceptionThrownPlaces(refs,
                                        factory.createType(myExceptionClasses[idx - 1]),
                                        myHighlightInPlace,
                                        myTypeFilter);
            }
            else {
              for (PsiClass exceptionClass : myExceptionClasses) {
                findExceptionThrownPlaces(refs,
                                          factory.createType(exceptionClass),
                                          myHighlightInPlace,
                                          myTypeFilter);
              }
            }


            new DoHighlightRunnable(refs, myProject, myTarget, myEditor, myFile).run();
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

  protected Runnable createHighlightAction(final Project project, PsiFile file, PsiElement[] targets, final Editor editor) {
    if (file instanceof PsiCompiledElement) file = (PsiFile)((PsiCompiledElement)file).getMirror();
    PsiElement target = targets[0];

    if (target instanceof PsiKeyword) {
      if (PsiKeyword.TRY.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiTryStatement)) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
        PsiTryStatement tryStatement = (PsiTryStatement)parent;

        final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(),
                                                                                      tryStatement.getTryBlock());

        return createChoosingRunnable(project, psiClassTypes, tryStatement.getTryBlock(), target, editor, file,
                                      ANY_TYPE);
      }

      if (PsiKeyword.CATCH.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiCatchSection)) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
        PsiTryStatement tryStatement = ((PsiCatchSection)parent).getTryStatement();

        PsiParameter param = ( (PsiCatchSection)parent).getParameter();
        if (param == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();

        final PsiClassType[] allThrownExceptions = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(),
                                                                                            tryStatement.getTryBlock());

        final PsiElement param1 = param;
        TypeFilter filter = new TypeFilter() {
          public boolean accept(PsiType type) {
            for (PsiParameter parameter : catchBlockParameters) {
              if (parameter != param1) {
                if (parameter.getType().isAssignableFrom(type)) return false;
              } else {
                return parameter.getType().isAssignableFrom(type);
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
                                      tryStatement.getTryBlock(), target, editor, file, filter);
      }

      if (PsiKeyword.THROWS.equals(target.getText())) {
        PsiElement parent = target.getParent().getParent();
        if (!(parent instanceof PsiMethod)) return EMPTY_HIGHLIGHT_RUNNABLE;
        PsiMethod method = (PsiMethod)parent;
        if (method.getBody() == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(method.getBody(),
                                                                                      method.getBody());

        return createChoosingRunnable(project, psiClassTypes, method.getBody(), target, editor, file, ANY_TYPE);
      }

      if (PsiKeyword.RETURN.equals(target.getText()) || PsiKeyword.THROW.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiThrowStatement)) return EMPTY_HIGHLIGHT_RUNNABLE;

        PsiMethod method = PsiTreeUtil.getParentOfType(target, PsiMethod.class);
        if (method == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        PsiCodeBlock body = method.getBody();
        try {
          ControlFlow flow = ControlFlowFactory.getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);

          List<PsiStatement> exitStatements = new ArrayList<PsiStatement>();
          ControlFlowUtil.findExitPointsAndStatements(flow, flow.getStartOffset(body), flow.getEndOffset(body), new IntArrayList(),
                                                      exitStatements,
                                                      new Class[]{PsiReturnStatement.class, PsiBreakStatement.class,
                                                                  PsiContinueStatement.class, PsiThrowStatement.class,
                                                                  PsiExpressionStatement.class});

          if (!exitStatements.contains(parent)) return EMPTY_HIGHLIGHT_RUNNABLE;

          return new DoHighlightExitPointsRunnable(project, editor, exitStatements.toArray(new PsiElement[exitStatements.size()]));
        }
        catch (AnalysisCanceledException e) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
      }
    }

    SearchScope searchScope = new LocalSearchScope(file);
    Collection<PsiReference> refs;
    if (target instanceof PsiMethod) {
      refs = MethodReferencesSearch.search((PsiMethod)target, searchScope, true).findAll();
    }
    else {
      refs = ReferencesSearch.search(target, searchScope, false).findAll();
    }

    return new DoHighlightRunnable(refs, project, target, editor, file);
  }

  private Runnable createChoosingRunnable(Project project, final PsiClassType[] psiClassTypes,
                                          PsiElement place, PsiElement target, Editor editor,
                                          PsiFile file, TypeFilter typeFilter) {
    if (psiClassTypes == null || psiClassTypes.length == 0) return EMPTY_HIGHLIGHT_RUNNABLE;

    final ChooseExceptionClassAndDoHighlightRunnable highlightRunnable =
      new ChooseExceptionClassAndDoHighlightRunnable(psiClassTypes, place, project, target, editor, file);
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

  private static void findExceptionThrownPlaces(final List<PsiReference> refs, final PsiType type, final PsiElement block,
                                         final TypeFilter typeFilter) {
    if (type instanceof PsiClassType) {
      block.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        public void visitThrowStatement(PsiThrowStatement statement) {
          super.visitThrowStatement(statement);
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(statement, block);
          if (exceptionTypes != null) {
            for (final PsiClassType actualType : exceptionTypes) {
              if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
                PsiExpression psiExpression = statement.getException();
                if (!(psiExpression instanceof PsiNewExpression)) continue;
                PsiJavaCodeReferenceElement ref = ((PsiNewExpression) psiExpression).getClassReference();
                if (refs.contains(ref)) continue;
                refs.add(ref);
              }
            }
          }
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (refs.contains(expression.getMethodExpression().getReference())) return;
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, block);
          for (final PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
              refs.add(expression.getMethodExpression().getReference());
            }
          }
        }

        public void visitNewExpression(PsiNewExpression expression) {
          super.visitNewExpression(expression);
          if (refs.contains(expression.getClassReference())) return;
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, block);
          for (PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
              refs.add(expression.getClassReference());
            }
          }
        }
      });
    }
  }

  private static void highlightReferences(Project project,
                                   PsiElement element,
                                   Collection<PsiReference> refs,
                                   Editor editor,
                                   PsiFile file) {

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    TextAttributes writeAttributes = manager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    setupFindModel(project);

    if (element instanceof PsiVariable) {
      List<PsiReference> readRefs = new ArrayList<PsiReference>();
      List<PsiReference> writeRefs = new ArrayList<PsiReference>();
      for (PsiReference ref : refs) {
        PsiElement refElement = ref.getElement();
        if (refElement instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
          writeRefs.add(ref);
        }
        else {
          readRefs.add(ref);
        }
      }
      doHighlightRefs(highlightManager, editor, readRefs, attributes);
      doHighlightRefs(highlightManager, editor, writeRefs, writeAttributes);
    }
    else {
      doHighlightRefs(highlightManager, editor, refs, attributes);
    }

    PsiElement identifier = getNameIdentifier(element);
    if (identifier != null && PsiUtil.isUnderPsiRoot(file, identifier)) {
      TextAttributes nameAttributes = attributes;
      if (element instanceof PsiVariable && ((PsiVariable)element).getInitializer() != null) {
        nameAttributes = writeAttributes;
      }
      doHighlightElements(highlightManager, editor, new PsiElement[]{identifier}, nameAttributes);
    }
    else if (element instanceof PsiKeyword) { //try, catch, throws.
      doHighlightElements(highlightManager, editor, new PsiElement[]{element}, attributes);
    }
  }

  private static void doHighlightElements(HighlightManager highlightManager,
                                   Editor editor,
                                   PsiElement[] elements,
                                   TextAttributes attributes) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    Document document = editor.getDocument();
    highlightManager.addOccurrenceHighlights(editor, elements, attributes, false, highlighters);
    for (int idx = 0; idx < highlighters.size(); idx++) {
      RangeHighlighter highlighter = highlighters.get(idx);
      int offset = elements[idx].getTextRange().getStartOffset();
      setLineTextErrorStripeTooltip(document, offset, highlighter);
    }
  }

  private static void doHighlightRefs(HighlightManager highlightManager,
                               Editor editor,
                               Collection<PsiReference> refs,
                               TextAttributes attributes) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    Document document = editor.getDocument();
    PsiReference[] refArray = refs.toArray(new PsiReference[refs.size()]);
    highlightManager.addOccurrenceHighlights(editor, refArray, attributes, false, highlighters);
    for (int idx = 0; idx < highlighters.size(); idx++) {
      RangeHighlighter highlighter = highlighters.get(idx);
      int offset = refArray[idx].getElement().getTextRange().getStartOffset() + refArray[idx].getRangeInElement().getStartOffset();
      setLineTextErrorStripeTooltip(document, offset, highlighter);
    }
  }

  public static PsiElement getNameIdentifier(PsiElement element) {
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getNameIdentifier();
    }
    if (element instanceof PsiMethod) {
      return ((PsiMethod)element).getNameIdentifier();
    }
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getNameIdentifier();
    }

    if (element.isPhysical() && element instanceof PsiNamedElement && element.getContainingFile() != null) {
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