// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FunctionalInterfaceSuggester;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.ui.MethodCellRenderer;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairConsumer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public class IntroduceParameterHandler extends IntroduceHandlerBase {
  private static final Logger LOG = Logger.getInstance(IntroduceParameterHandler.class);
  private JBPopup myEnclosingMethodsPopup;
  private InplaceIntroduceParameterPopup myInplaceIntroduceParameterPopup;

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ElementToWorkOn.processElementToWorkOn(editor, file, getRefactoringName(), HelpID.INTRODUCE_PARAMETER, project,
                                           new ElementToWorkOn.ElementsProcessor<>() {
                                             @Override
                                             public boolean accept(ElementToWorkOn el) {
                                               return true;
                                             }

                                             @Override
                                             public void pass(final ElementToWorkOn elementToWorkOn) {
                                               if (elementToWorkOn == null) {
                                                 return;
                                               }

                                               if (elementToWorkOn.getLocalVariable() == null && elementToWorkOn.getExpression() == null) {
                                                 if (!introduceStrategy(project, editor, file)) {
                                                   ElementToWorkOn.showNothingSelectedErrorMessage(editor, getRefactoringName(),
                                                                                                   HelpID.INTRODUCE_PARAMETER, project);
                                                 }
                                                 return;
                                               }

                                               final PsiExpression expr = elementToWorkOn.getExpression();
                                               final PsiLocalVariable localVar = elementToWorkOn.getLocalVariable();
                                               final boolean isInvokedOnDeclaration = elementToWorkOn.isInvokedOnDeclaration();

                                               invoke(editor, project, expr, localVar, isInvokedOnDeclaration);
                                             }
                                           });
  }

  @Override
  protected boolean invokeImpl(Project project, PsiExpression tempExpr, Editor editor) {
    return invoke(editor, project, tempExpr, null, false);
  }

  @Override
  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    return invoke(editor, project, null, localVariable, true);
  }

  private boolean invoke(final Editor editor, final Project project, final PsiExpression expr,
                         PsiLocalVariable localVar, boolean invokedOnDeclaration) {
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).hasUncommitedDocuments());
    PsiMethod method;
    if (expr != null) {
      method = Util.getContainingMethod(expr);
    }
    else {
      method = Util.getContainingMethod(localVar);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    if (expr == null && localVar == null) {
      String message =  RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.block.should.represent.an.expression"));
      showErrorMessage(project, message, editor);
      return false;
    }

    if (localVar != null) {
      final PsiElement parent = localVar.getParent();
      if (!(parent instanceof PsiDeclarationStatement)) {
        String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
        showErrorMessage(project, message, editor);
        return false;
      }
    }

    if (method == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context",
                                                                                            getRefactoringName()));
      showErrorMessage(project, message, editor);
      return false;
    }

    final PsiType typeByExpression = invokedOnDeclaration ? null : CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (!invokedOnDeclaration && (typeByExpression == null || LambdaUtil.notInferredType(typeByExpression))) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("type.of.the.selected.expression.cannot.be.determined"));
      showErrorMessage(project, message, editor);
      return false;
    }

    if (!invokedOnDeclaration && PsiTypes.voidType().equals(typeByExpression)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(project, message, editor);
      return false;
    }

    final List<PsiMethod> validEnclosingMethods = CommonJavaRefactoringUtil.getEnclosingMethods(method);
    if (validEnclosingMethods.isEmpty()) {
      return false;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return false;

    final Introducer introducer = new Introducer(project, expr, localVar, editor);
    final AbstractInplaceIntroducer inplaceIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
    if (inplaceIntroducer instanceof InplaceIntroduceParameterPopup introduceParameterPopup) {
      introducer.introduceParameter(introduceParameterPopup.getMethodToIntroduceParameter(),
                                    introduceParameterPopup.getMethodToSearchFor());
      return true;
    }

    chooseMethodToIntroduceParameter(editor, validEnclosingMethods,
                                     (methodToSearchIn, methodToSearchFor) -> introducer.introduceParameter(methodToSearchIn, methodToSearchFor));

    return true;
  }

  private void chooseMethodToIntroduceParameter(final Editor editor,
                                                @NotNull List<? extends PsiMethod> validEnclosingMethods,
                                                @NotNull PairConsumer<? super PsiMethod, ? super PsiMethod> consumer) {
    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (validEnclosingMethods.size() == 1 || unitTestMode) {
      final PsiMethod methodToIntroduceParameterTo = validEnclosingMethods.get(0);
      if (methodToIntroduceParameterTo.findDeepestSuperMethod() == null || unitTestMode) {
        consumer.consume(methodToIntroduceParameterTo, methodToIntroduceParameterTo);
        return;
      }
    }

    final JPanel panel = new JPanel(new BorderLayout());
    final JCheckBox superMethod = new JCheckBox(JavaRefactoringBundle.message("introduce.parameter.super.method.checkbox"), true);
    panel.add(superMethod, BorderLayout.SOUTH);
    final JBList<PsiMethod> list = new JBList<>(validEnclosingMethods.toArray(PsiMethod.EMPTY_ARRAY));
    list.setVisibleRowCount(5);
    list.setCellRenderer(new MethodCellRenderer());
    list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    final List<RangeHighlighter> highlighters = new ArrayList<>();
    list.addListSelectionListener(__ -> {
      final PsiMethod selectedMethod = list.getSelectedValue();
      if (selectedMethod == null) return;
      dropHighlighters(highlighters);
      updateView(selectedMethod, editor, highlighters, superMethod);
    });
    updateView(validEnclosingMethods.get(0), editor, highlighters, superMethod);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list);
    scrollPane.setBorder(null);
    panel.add(scrollPane, BorderLayout.CENTER);

    final List<Pair<ActionListener, KeyStroke>>
      keyboardActions = Collections.singletonList(Pair.create(__ -> {
        final PsiMethod methodToSearchIn = list.getSelectedValue();
        if (myEnclosingMethodsPopup != null && myEnclosingMethodsPopup.isVisible()) {
          myEnclosingMethodsPopup.cancel();
        }

        final PsiMethod methodToSearchFor = superMethod.isEnabled() && superMethod.isSelected()
                                            ? methodToSearchIn.findDeepestSuperMethod() : methodToSearchIn;
        consumer.consume(methodToSearchIn, methodToSearchFor);
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
    myEnclosingMethodsPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
      .setTitle(RefactoringBundle.message("refactoring.introduce.parameter.popup.title"))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setKeyboardActions(keyboardActions).addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          dropHighlighters(highlighters);
        }
      }).createPopup();
    myEnclosingMethodsPopup.showInBestPositionFor(editor);
  }

  private static void updateView(PsiMethod selectedMethod,
                                 Editor editor,
                                 List<? super RangeHighlighter> highlighters,
                                 JCheckBox superMethod) {
    final MarkupModel markupModel = editor.getMarkupModel();
    final PsiIdentifier nameIdentifier = selectedMethod.getNameIdentifier();
    if (nameIdentifier != null) {
      final TextRange textRange = nameIdentifier.getTextRange();
      final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
        EditorColors.SEARCH_RESULT_ATTRIBUTES, textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1,
        HighlighterTargetArea.EXACT_RANGE);
      highlighters.add(rangeHighlighter);
    }
    superMethod.setEnabled(selectedMethod.findDeepestSuperMethod() != null);
  }

  private static void dropHighlighters(List<? extends RangeHighlighter> highlighters) {
    for (RangeHighlighter highlighter : highlighters) {
      highlighter.dispose();
    }
    highlighters.clear();
  }

  @NotNull
  static NameSuggestionsGenerator createNameSuggestionGenerator(final PsiExpression expr,
                                                                final String propName,
                                                                final Project project,
                                                                final String enteredName) {
    return type -> {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      SuggestedNameInfo info = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, propName, expr != null && expr.isValid() ? expr : null, type);
      if (expr != null && expr.isValid()) {
        info = codeStyleManager.suggestUniqueVariableName(info, expr, true);
      }
      final String[] strings = JavaNameSuggestionUtil.appendUnresolvedExprName(JavaCompletionUtil
        .completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, info), expr);
      return new SuggestedNameInfo.Delegate(enteredName != null ? ArrayUtil.mergeArrays(new String[]{enteredName}, strings): strings, info);
    };
  }

  private static void showErrorMessage(Project project, @NlsContexts.DialogMessage String message, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INTRODUCE_PARAMETER);
  }


  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    // Never called
    /* do nothing */
  }


  private class Introducer {

    private final Project myProject;

    private PsiExpression myExpr;
    private PsiLocalVariable myLocalVar;
    private final Editor myEditor;

    Introducer(Project project,
                      PsiExpression expr,
                      PsiLocalVariable localVar,
                      Editor editor) {
      myProject = project;
      myExpr = expr;
      myLocalVar = localVar;
      myEditor = editor;
    }

    public void introduceParameter(PsiMethod method, PsiMethod methodToSearchFor) {
      ExpressionOccurrenceManager occurrenceManager = myExpr != null ? new ExpressionOccurrenceManager(myExpr, method, null) : null;
      PsiExpression[] occurrences;
      if (myExpr != null) {
        occurrences = occurrenceManager.findExpressionOccurrences();
      }
      else { // local variable
        occurrences = CodeInsightUtil.findReferenceExpressions(method, myLocalVar);
      }

      if (myExpr != null && AbstractInplaceIntroducer.getActiveIntroducer(myEditor) == null) {
        IntroduceVariableBase.OccurrencesInfo occurrencesInfo = new IntroduceVariableBase.OccurrencesInfo(occurrences, false);
        LinkedHashMap<IntroduceVariableBase.JavaReplaceChoice, List<PsiExpression>> occurrencesMap = occurrencesInfo.buildOccurrencesMap(myExpr);
        IntroduceVariableBase.createOccurrencesChooser(myEditor).showChooser(new Pass<>() {
          @Override
          public void pass(IntroduceVariableBase.JavaReplaceChoice choice) {
            PsiExpression[] selectedOccurrences = choice.filter(occurrenceManager);
            introduceParameter(method, methodToSearchFor, selectedOccurrences, choice);
          }
        }, occurrencesMap, RefactoringBundle.message("replace.multiple.occurrences.found"));
      }
      else {
        introduceParameter(method, methodToSearchFor, occurrences, IntroduceVariableBase.JavaReplaceChoice.ALL);
      }
    }

    private void introduceParameter(PsiMethod method,
                                    PsiMethod methodToSearchFor,
                                    PsiExpression[] occurrences,
                                    @NotNull IntroduceVariableBase.JavaReplaceChoice replaceChoice) {
      String enteredName = null;
      boolean delegate = false;
      PsiType initializerType = IntroduceParameterProcessor.getInitializerType(null, myExpr, myLocalVar);

      final var activeIntroducer = (InplaceIntroduceParameterPopup)AbstractInplaceIntroducer.getActiveIntroducer(myEditor);
      if (activeIntroducer != null) {
        activeIntroducer.stopIntroduce(myEditor);
        myExpr = activeIntroducer.getExpr();
        myLocalVar = (PsiLocalVariable)activeIntroducer.getLocalVariable();
        occurrences = activeIntroducer.getOccurrences();
        enteredName = activeIntroducer.getInputName();
        replaceChoice = activeIntroducer.getReplaceChoice();
        delegate = activeIntroducer.isGenerateDelegate();
        initializerType = activeIntroducer.getType();
      }

      boolean mustBeFinal = false;
      if (myExpr != null) {
        final PsiElement parent = myExpr.getUserData(ElementToWorkOn.PARENT);
        mustBeFinal = parent != null && PsiTreeUtil.getParentOfType(parent, PsiClass.class, PsiMethod.class) != method;
      }
      for (PsiExpression occurrence : occurrences) {
        if (occurrence.isPhysical() && PsiTreeUtil.getParentOfType(occurrence, PsiClass.class, PsiMethod.class) != method) {
          mustBeFinal = true;
          break;
        }
      }

      final String propName = myLocalVar != null ? JavaCodeStyleManager
        .getInstance(myProject).variableNameToPropertyName(myLocalVar.getName(), VariableKind.LOCAL_VARIABLE) : null;

      boolean isInplaceAvailableOnDataContext = myEditor != null && myEditor.getSettings().isVariableInplaceRenameEnabled();

      if (myExpr != null) {
        isInplaceAvailableOnDataContext &= myExpr.isPhysical();
      }

      if (isInplaceAvailableOnDataContext && activeIntroducer == null) {
        myInplaceIntroduceParameterPopup =
          new InplaceIntroduceParameterPopup(myProject, myEditor,
                                             createTypeSelectorManager(occurrences, initializerType),
                                             myExpr, myLocalVar, method, methodToSearchFor, occurrences,
                                             getParamsToRemove(method, occurrences),
                                             mustBeFinal, 
                                             replaceChoice);
        if (myInplaceIntroduceParameterPopup.startInplaceIntroduceTemplate()) {
          return;
        }
      }
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        @NonNls String parameterName = "anObject";
        PsiExpression initializer = myLocalVar != null && myExpr == null ? myLocalVar.getInitializer() : myExpr;
        new IntroduceParameterProcessor(myProject, method, methodToSearchFor, initializer, myExpr, myLocalVar, true, parameterName,
                                        replaceChoice, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, mustBeFinal,
                                        false, false, null,
                                        getParamsToRemove(method, occurrences)).run();
      }
      else {
        if (myEditor != null) {
          RefactoringUtil.highlightAllOccurrences(myProject, occurrences, myEditor);
        }

        final List<UsageInfo> classMemberRefs = new ArrayList<>();
        if (myExpr != null) {
          Util.analyzeExpression(myExpr, new ArrayList<>(), classMemberRefs, new ArrayList<>());
        }

        showDialog(method, methodToSearchFor, occurrences, replaceChoice, delegate, initializerType, mustBeFinal,
                   classMemberRefs, createNameSuggestionGenerator(myExpr, propName, myProject, enteredName));
      }
    }

    private void showDialog(PsiMethod method,
                            PsiMethod methodToSearchFor,
                            PsiExpression[] occurrences,
                            IntroduceVariableBase.JavaReplaceChoice replaceChoice,
                            boolean delegate,
                            PsiType initializerType,
                            boolean mustBeFinal,
                            List<? extends UsageInfo> classMemberRefs, NameSuggestionsGenerator nameSuggestionGenerator) {
      final IntroduceParameterDialog dialog =
        new IntroduceParameterDialog(myProject, classMemberRefs, occurrences, myLocalVar, myExpr,
                                     nameSuggestionGenerator,
                                     createTypeSelectorManager(occurrences, initializerType), methodToSearchFor, method, getParamsToRemove(method, occurrences), mustBeFinal);
      dialog.setReplaceAllOccurrences(replaceChoice);
      dialog.setGenerateDelegate(delegate);
      if (dialog.showAndGet()) {
        final Runnable cleanSelectionRunnable = () -> {
          if (myEditor != null && !myEditor.isDisposed()) {
            myEditor.getSelectionModel().removeSelection();
          }
        };
        ApplicationManager.getApplication().invokeLater(cleanSelectionRunnable, ModalityState.any());
      }
    }

    private TypeSelectorManagerImpl createTypeSelectorManager(PsiExpression[] occurrences, PsiType initializerType) {
      return myExpr != null ? new TypeSelectorManagerImpl(myProject, initializerType, myExpr, occurrences)
                            : new TypeSelectorManagerImpl(myProject, initializerType, occurrences);
    }

    private IntList getParamsToRemove(PsiMethod method, PsiExpression[] occurrences) {
      PsiExpression expressionToRemoveParamFrom = myExpr;
      if (myExpr == null) {
        expressionToRemoveParamFrom = myLocalVar.getInitializer();
      }
      return expressionToRemoveParamFrom == null ? new IntArrayList() : Util
        .findParametersToRemove(method, expressionToRemoveParamFrom, occurrences);
    }
  }

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroduceParameterPopup;
  }

  @VisibleForTesting
  private boolean introduceStrategy(final Project project, final Editor editor, PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      final PsiElement[] elements = CodeInsightUtil.findStatementsInRange(file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      return introduceStrategy(project, editor, file, elements);
    }
    return false;
  }

  @VisibleForTesting
  public boolean introduceStrategy(@NotNull Project project, final Editor editor, @NotNull PsiFile file, final PsiElement @NotNull [] elements) {
    if (elements.length > 0) {
      final AbstractInplaceIntroducer inplaceIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
      if (inplaceIntroducer instanceof InplaceIntroduceParameterPopup) {
        return false;
      }
      final PsiMethod containingMethod = Util.getContainingMethod(elements[0]);
      if (containingMethod == null) {
        return false;
      }
      final List<PsiMethod> enclosingMethods = CommonJavaRefactoringUtil.getEnclosingMethods(containingMethod);
      if (enclosingMethods.isEmpty()) {
        return false;
      }

      final PsiElement[] elementsCopy = getElementsInCopy(project, file, elements);
      final PsiMethod containingMethodCopy = Util.getContainingMethod(elementsCopy[0]);
      LOG.assertTrue(containingMethodCopy != null);
      final List<PsiMethod> enclosingMethodsInCopy = CommonJavaRefactoringUtil.getEnclosingMethods(containingMethodCopy);
      final MyExtractMethodProcessor processor = new MyExtractMethodProcessor(project, editor, elementsCopy,
                                                                              enclosingMethodsInCopy.get(enclosingMethodsInCopy.size() - 1));
      try {
        if (!processor.prepare()) return false;
        processor.showDialog();

        //provide context for generated method to check exceptions compatibility
        final PsiMethod emptyMethod = processor.generateEmptyMethod("name", elements[0]);
        final Collection<? extends PsiType> types = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(emptyMethod);
        if (types.isEmpty()) {
          return false;
        }

        if (types.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
          final PsiType next = types.iterator().next();
          functionalInterfaceSelected(next, enclosingMethods, project, editor, processor, elements);
        }
        else {
          final Map<PsiClass, PsiType> classes = new LinkedHashMap<>();
          for (PsiType type : types) {
            classes.put(PsiUtil.resolveClassInType(type), type);
          }
          final PsiClass[] psiClasses = classes.keySet().toArray(PsiClass.EMPTY_ARRAY);
          final String methodSignature =
            PsiFormatUtil.formatMethod(emptyMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_TYPE);
          final PsiType returnType = emptyMethod.getReturnType();
          LOG.assertTrue(returnType != null);
          final String title = RefactoringBundle.message("refactoring.introduce.parameter.interface.chooser.popup.title",
                                                             methodSignature, returnType.getPresentableText());
          new PsiTargetNavigator<>(psiClasses).createPopup(project, title,
                                               new PsiElementProcessor<>() {
                                                 @Override
                                                 public boolean execute(@NotNull PsiClass psiClass) {
                                                   functionalInterfaceSelected(classes.get(psiClass), enclosingMethods, project, editor,
                                                                               processor,
                                                                               elements);
                                                   return true;
                                                 }
                                               }).showInBestPositionFor(editor);
          return true;
        }

        return true;
      }
      catch (IncorrectOperationException | PrepareFailedException ignore) {}
    }
    return false;
  }

  public static PsiElement @NotNull [] getElementsInCopy(@NotNull Project project, @NotNull PsiFile file, PsiElement @NotNull [] elements) {
    return getElementsInCopy(project, file, elements, true);
  }

  public static PsiElement @NotNull [] getElementsInCopy(@NotNull Project project, @NotNull PsiFile file, PsiElement @NotNull [] elements, boolean reuseNonPhysical) {
    final PsiElement[] elementsCopy;
    if (reuseNonPhysical && !elements[0].isPhysical()) {
      elementsCopy = elements;
    }
    else {
      final PsiFile copy = PsiFileFactory.getInstance(project)
        .createFileFromText(file.getName(), file.getFileType(), file.getText(), file.getModificationStamp(), false);
      final TextRange range = new TextRange(elements[0].getTextRange().getStartOffset(),
                                            elements[elements.length - 1].getTextRange().getEndOffset());
      final PsiExpression exprInRange = CodeInsightUtil.findExpressionInRange(copy, range.getStartOffset(), range.getEndOffset());
      elementsCopy = exprInRange != null
                     ? new PsiElement[]{exprInRange}
                     : CodeInsightUtil.findStatementsInRange(copy, range.getStartOffset(), range.getEndOffset());
    }
    if (elementsCopy.length == 1 && elementsCopy[0].getUserData(ElementToWorkOn.PARENT) == null) {
        elementsCopy[0].putUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL, true);
      }

    return elementsCopy;
  }

  private void functionalInterfaceSelected(final PsiType selectedType,
                                           final List<? extends PsiMethod> enclosingMethods,
                                           final Project project,
                                           final Editor editor,
                                           final MyExtractMethodProcessor processor,
                                           final PsiElement[] elements) {
    final PairConsumer<PsiMethod, PsiMethod> consumer =
      (methodToIntroduceParameter, methodToSearchFor) -> introduceWrappedCodeBlockParameter(methodToIntroduceParameter, methodToSearchFor, editor, project, selectedType, processor, elements);
    chooseMethodToIntroduceParameter(editor, enclosingMethods, consumer);
  }

  private void introduceWrappedCodeBlockParameter(PsiMethod methodToIntroduceParameter,
                                                  PsiMethod methodToSearchFor, Editor editor,
                                                  final Project project,
                                                  final PsiType selectedType,
                                                  final ExtractMethodProcessor processor,
                                                  final PsiElement[] elements) {
    if (!elements[0].isValid()) {
      return;
    }
    final PsiElement commonParent = findCommonParent(elements);
    if (commonParent == null) {
      LOG.error("Should have common parent:" + Arrays.toString(elements));
      return;
    }
    final RangeMarker marker = editor.getDocument().createRangeMarker(commonParent.getTextRange());

    final PsiElement[] copyElements = processor.getElements();
    final PsiElement containerCopy = findCommonParent(copyElements);
    if (containerCopy == null) {
      LOG.error("Should have common parent:" + Arrays.toString(copyElements));
      return;
    }

    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(selectedType);
    final PsiClass wrapperClass = resolveResult.getElement();
    LOG.assertTrue(wrapperClass != null);

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final Ref<String> suffixText = new Ref<>();
    final Ref<String> prefixText = new Ref<>();
    final Ref<String> methodText = new Ref<>();
    WriteCommandAction.runWriteCommandAction(project, () -> {
      final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(wrapperClass);
      LOG.assertTrue(method != null);
      final String interfaceMethodName = method.getName();
      processor.setMethodName(interfaceMethodName);
      processor.doExtract();

      final PsiMethod extractedMethod = processor.getExtractedMethod();
      final PsiParameter[] parameters = extractedMethod.getParameterList().getParameters();
      final PsiParameter[] interfaceParameters = method.getParameterList().getParameters();
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      for (int i = 0; i < interfaceParameters.length; i++) {
        final PsiTypeElement typeAfterInterface = factory.createTypeElement(substitutor.substitute(interfaceParameters[i].getType()));
        final PsiTypeElement typeElement = parameters[i].getTypeElement();
        if (typeElement != null) {
          typeElement.replace(typeAfterInterface);
        }
      }
      methodText.set(extractedMethod.getText());

      final PsiMethodCallExpression methodCall = processor.getMethodCall();
      prefixText.set(containerCopy.getText().substring(0, methodCall.getTextRange().getStartOffset() - containerCopy.getTextRange().getStartOffset()));
      suffixText.set("." + methodCall.getText() + containerCopy.getText().substring(methodCall.getTextRange().getEndOffset() - containerCopy.getTextRange().getStartOffset()));
    });


    PsiExpression expression = factory
      .createExpressionFromText("new " + selectedType.getCanonicalText() + "() {" + methodText.get() + "}",
                                elements[0]);
    expression = (PsiExpression)JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression);

    expression.putUserData(ElementToWorkOn.PARENT, commonParent);
    expression.putUserData(ElementToWorkOn.PREFIX, prefixText.get());
    expression.putUserData(ElementToWorkOn.SUFFIX, suffixText.get());
    expression.putUserData(ElementToWorkOn.TEXT_RANGE, marker);
    expression.putUserData(ElementToWorkOn.EXPR_RANGE, elements.length == 1 ? elements[0].getTextRange() : null);

    new Introducer(project, expression, null, editor)
      .introduceParameter(methodToIntroduceParameter, methodToSearchFor);
  }

  @Nullable
  private static PsiElement findCommonParent(PsiElement[] copyElements) {
    if (copyElements.length > 1) {
      return PsiTreeUtil.findCommonParent(copyElements);
    }
    else {
      PsiElement parent = copyElements[0].getUserData(ElementToWorkOn.PARENT);
      if (parent == null) {
        parent = copyElements[0].getParent();
      }
      return PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class, false);
    }
  }

  private static class MyExtractMethodProcessor extends ExtractMethodProcessor {
    private final PsiMethod myTopEnclosingMethod;

    MyExtractMethodProcessor(Project project, Editor editor, PsiElement[] elements, @NotNull PsiMethod topEnclosing) {
      super(project, editor, elements, null, getRefactoringName(), null, null);
      myTopEnclosingMethod = topEnclosing;
    }

    @Override
    protected AbstractExtractDialog createExtractMethodDialog(boolean direct) {
      return new MyAbstractExtractDialog();
    }

    @Override
    protected boolean isNeedToChangeCallContext() {
      return false;
    }

    @Override
    public Boolean hasDuplicates() {
      return false;
    }

    @Override
    public boolean isStatic() {
      return false;
    }

    @Override
    protected boolean isFoldingApplicable() {
      return false;
    }

    @Override
    protected PsiMethod addExtractedMethod(PsiMethod newMethod) {
      return newMethod;
    }

    @Override
    public boolean prepare(@Nullable Consumer<? super ExtractMethodProcessor> pass) throws PrepareFailedException {
      final boolean prepare = super.prepare(pass);
      if (prepare) {
        if (myNotNullConditionalCheck || myNullConditionalCheck) {
          return false;
        }
      }
      return prepare;
    }

    private class MyAbstractExtractDialog implements AbstractExtractDialog {
      @NotNull
      @Override
      public String getChosenMethodName() {
        return "name";
      }

      @Override
      public VariableData[] getChosenParameters() {
        final InputVariables inputVariables = getInputVariables();
        List<VariableData> datas = new ArrayList<>();
        for (VariableData data : inputVariables.getInputVariables()) {
          final PsiVariable variable = data.variable;
          if (variable instanceof PsiParameter && myTopEnclosingMethod.equals(((PsiParameter)variable).getDeclarationScope())) {
            continue;
          }
          datas.add(data);
        }
        return datas.toArray(new VariableData[0]);
      }

      @NotNull
      @Override
      public String getVisibility() {
        return PsiModifier.PUBLIC;
      }

      @Override
      public boolean isMakeStatic() {
        return false;
      }

      @Override
      public boolean isChainedConstructor() {
        return false;
      }

      @Override
      public PsiType getReturnType() {
        return null;
      }

      @Override
      public void show() {}

      @Override
      public boolean isOK() {
        return true;
      }
    }
  }

  static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("introduce.parameter.title");
  }
}
