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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 13:36:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.*;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceField.InplaceIntroduceFieldPopup;
import com.intellij.refactoring.ui.MethodCellRenderer;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class IntroduceParameterHandler extends IntroduceHandlerBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceParameter.IntroduceParameterHandler");
  static final String REFACTORING_NAME = RefactoringBundle.message("introduce.parameter.title");
  private Project myProject;
  private JBPopup myEnclosingMethodsPopup;
  private InplaceIntroduceParameterPopup myInplaceIntroduceParameterPopup;

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ElementToWorkOn.processElementToWorkOn(editor, file, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER, project, new Pass<ElementToWorkOn>() {
      @Override
      public void pass(final ElementToWorkOn elementToWorkOn) {
        if (elementToWorkOn == null) return;

        final PsiExpression expr = elementToWorkOn.getExpression();
        final PsiLocalVariable localVar = elementToWorkOn.getLocalVariable();
        final boolean isInvokedOnDeclaration = elementToWorkOn.isInvokedOnDeclaration();

        invoke(editor, project, expr, localVar, isInvokedOnDeclaration);
      }
    });
  }

  protected boolean invokeImpl(Project project, PsiExpression tempExpr, Editor editor) {
    return invoke(editor, project, tempExpr, null, false);
  }

  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    return invoke(editor, project, null, localVariable, true);
  }

  private boolean invoke(final Editor editor, final Project project, final PsiExpression expr,
                         PsiLocalVariable localVar, boolean invokedOnDeclaration) {
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).hasUncommitedDocuments());
    PsiMethod method;
    if (expr != null) {
      final PsiElement physicalElement = expr.getUserData(ElementToWorkOn.PARENT);
      method = Util.getContainingMethod(physicalElement != null ? physicalElement : expr);
    }
    else {
      method = Util.getContainingMethod(localVar);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    myProject = project;
    if (expr == null && localVar == null) {
      String message =  RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.an.expression"));
      showErrorMessage(myProject, message, editor);
      return false;
    }

    if (localVar != null) {
      final PsiElement parent = localVar.getParent();
      if (!(parent instanceof PsiDeclarationStatement)) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
        showErrorMessage(myProject, message, editor);
        return false;
      }
    }

    if (method == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context", REFACTORING_NAME));
      showErrorMessage(myProject, message, editor);
      return false;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return false;

    final PsiType typeByExpression = invokedOnDeclaration ? null : RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (!invokedOnDeclaration && typeByExpression == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("type.of.the.selected.expression.cannot.be.determined"));
      showErrorMessage(myProject, message, editor);
      return false;
    }

    if (!invokedOnDeclaration && PsiType.VOID.equals(typeByExpression)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(project, message, editor);
      return false;
    }

    final List<PsiMethod> validEnclosingMethods = getEnclosingMethods(method);
    if (validEnclosingMethods.isEmpty()) {
      return false;
    }
    final Introducer introducer = new Introducer(project, expr, localVar, editor);
    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (validEnclosingMethods.size() == 1 || unitTestMode) {
      final PsiMethod methodToIntroduceParameterTo = validEnclosingMethods.get(0);
      if (methodToIntroduceParameterTo.findDeepestSuperMethod() == null || unitTestMode) {
        introducer.introduceParameter(methodToIntroduceParameterTo, methodToIntroduceParameterTo);
        return true;
      }
    }

    chooseMethodToIntroduceParameter(editor, validEnclosingMethods, introducer);

    return true;
  }

  private void chooseMethodToIntroduceParameter(final Editor editor,
                                                final List<PsiMethod> validEnclosingMethods,
                                                final Introducer introducer) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JCheckBox superMethod = new JCheckBox("Use super method of", true);
    superMethod.setMnemonic('U');
    panel.add(superMethod, BorderLayout.SOUTH);
    final JBList list = new JBList(validEnclosingMethods.toArray());
    list.setVisibleRowCount(5);
    list.setCellRenderer(new MethodCellRenderer());
    list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    final List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    final TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    list.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        final PsiMethod selectedMethod = (PsiMethod)list.getSelectedValue();
        if (selectedMethod == null) return;
        dropHighlighters(highlighters);
        updateView(selectedMethod, editor, attributes, highlighters, superMethod);
      }
    });
    updateView(validEnclosingMethods.get(0), editor, attributes, highlighters, superMethod);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list);
    scrollPane.setBorder(null);
    panel.add(scrollPane, BorderLayout.CENTER);

    final List<Pair<ActionListener, KeyStroke>>
      keyboardActions = Collections.singletonList(Pair.<ActionListener, KeyStroke>create(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final PsiMethod methodToSearchIn = (PsiMethod)list.getSelectedValue();
          if (myEnclosingMethodsPopup != null && myEnclosingMethodsPopup.isVisible()) {
            myEnclosingMethodsPopup.cancel();
          }

          final PsiMethod methodToSearchFor = superMethod.isEnabled() && superMethod.isSelected()
                                              ? methodToSearchIn.findDeepestSuperMethod() : methodToSearchIn;
          Runnable runnable = new Runnable() {
            public void run() {
              introducer.introduceParameter(methodToSearchIn, methodToSearchFor);
            }
          };
          IdeFocusManager.findInstance().doWhenFocusSettlesDown(runnable);
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
    myEnclosingMethodsPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
      .setTitle("Introduce parameter to method")
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setKeyboardActions(keyboardActions).addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          dropHighlighters(highlighters);
        }
      }).createPopup();
    myEnclosingMethodsPopup.showInBestPositionFor(editor);
  }

  private static void updateView(PsiMethod selectedMethod,
                                 Editor editor,
                                 TextAttributes attributes,
                                 List<RangeHighlighter> highlighters,
                                 JCheckBox superMethod) {
    final MarkupModel markupModel = editor.getMarkupModel();
    final PsiIdentifier nameIdentifier = selectedMethod.getNameIdentifier();
    if (nameIdentifier != null) {
      final TextRange textRange = nameIdentifier.getTextRange();
      final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
        textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1,
        attributes,
        HighlighterTargetArea.EXACT_RANGE);
      highlighters.add(rangeHighlighter);
    }
    superMethod.setEnabled(selectedMethod.findDeepestSuperMethod() != null);
  }

  private static void dropHighlighters(List<RangeHighlighter> highlighters) {
    for (RangeHighlighter highlighter : highlighters) {
      highlighter.dispose();
    }
    highlighters.clear();
  }

  protected static NameSuggestionsGenerator createNameSuggestionGenerator(final PsiExpression expr,
                                                                          final String propName,
                                                                          final Project project,
                                                                          final String enteredName) {
    return new NameSuggestionsGenerator() {
      public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        final SuggestedNameInfo info = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, propName, expr, type);
        final String[] strings = JavaCompletionUtil
          .completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, info);
        return new SuggestedNameInfo.Delegate(enteredName != null ? ArrayUtil.mergeArrays(new String[]{enteredName}, strings): strings, info);
      }

    };
  }

  private static void showErrorMessage(Project project, String message, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER);
  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // Never called
    /* do nothing */
  }

  public static List<PsiMethod> getEnclosingMethods(PsiMethod nearest) {
    List<PsiMethod> enclosingMethods = new ArrayList<PsiMethod>();
    enclosingMethods.add(nearest);
    PsiMethod method = nearest;
    while(true) {
      method = PsiTreeUtil.getParentOfType(method, PsiMethod.class, true);
      if (method == null) break;
      enclosingMethods.add(method);
    }
    if (enclosingMethods.size() > 1) {
      List<PsiMethod> methodsNotImplementingLibraryInterfaces = new ArrayList<PsiMethod>();
      for(PsiMethod enclosing: enclosingMethods) {
        PsiMethod[] superMethods = enclosing.findDeepestSuperMethods();
        boolean libraryInterfaceMethod = false;
        for(PsiMethod superMethod: superMethods) {
          libraryInterfaceMethod |= isLibraryInterfaceMethod(superMethod);
        }
        if (!libraryInterfaceMethod) {
          methodsNotImplementingLibraryInterfaces.add(enclosing);
        }
      }
      if (methodsNotImplementingLibraryInterfaces.size() > 0) {
        return methodsNotImplementingLibraryInterfaces;
      }
    }
    return enclosingMethods;
  }


  @Nullable
  public static PsiMethod chooseEnclosingMethod(@NotNull PsiMethod method) {
    final List<PsiMethod> validEnclosingMethods = getEnclosingMethods(method);
    if (validEnclosingMethods.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      final EnclosingMethodSelectionDialog dialog = new EnclosingMethodSelectionDialog(method.getProject(), validEnclosingMethods);
      dialog.show();
      if (!dialog.isOK()) return null;
      method = dialog.getSelectedMethod();
    }
    else if (validEnclosingMethods.size() == 1) {
      method = validEnclosingMethods.get(0);
    }
    return method;
  }

  private static boolean isLibraryInterfaceMethod(final PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.getManager().isInProject(method);
  }

  private class Introducer {

    private final Project myProject;

    private PsiExpression myExpr;
    private PsiLocalVariable myLocalVar;
    private final Editor myEditor;

    public Introducer(Project project,
                      PsiExpression expr,
                      PsiLocalVariable localVar,
                      Editor editor) {
      myProject = project;
      myExpr = expr;
      myLocalVar = localVar;
      myEditor = editor;
    }

    public void introduceParameter(PsiMethod method, PsiMethod methodToSearchFor) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, methodToSearchFor)) return;

      PsiExpression[] occurences;
      if (myExpr != null) {
        occurences = new ExpressionOccurenceManager(myExpr, method, null).findExpressionOccurrences();
      }
      else { // local variable
        occurences = CodeInsightUtil.findReferenceExpressions(method, myLocalVar);
      }


      boolean mustBeFinal = false;
      if (myLocalVar != null) {
        for(PsiExpression occurrence: occurences) {
          if (PsiTreeUtil.getParentOfType(occurrence, PsiClass.class, PsiMethod.class) != method) {
            mustBeFinal = true;
            break;
          }
        }
      }

      List<UsageInfo> localVars = new ArrayList<UsageInfo>();
      List<UsageInfo> classMemberRefs = new ArrayList<UsageInfo>();
      List<UsageInfo> params = new ArrayList<UsageInfo>();


      if (myExpr != null) {
        Util.analyzeExpression(myExpr, localVars, classMemberRefs, params);
      }

      final String propName = myLocalVar != null ? JavaCodeStyleManager
        .getInstance(myProject).variableNameToPropertyName(myLocalVar.getName(), VariableKind.LOCAL_VARIABLE) : null;
      final PsiType initializerType = IntroduceParameterProcessor.getInitializerType(null, myExpr, myLocalVar);

      TypeSelectorManagerImpl typeSelectorManager = myExpr != null
                                                ? new TypeSelectorManagerImpl(myProject, initializerType, myExpr, occurences)
                                                : new TypeSelectorManagerImpl(myProject, initializerType, occurences);

      boolean isInplaceAvailableOnDataContext = myEditor != null && myEditor.getSettings().isVariableInplaceRenameEnabled();

      if (myExpr != null) {
        isInplaceAvailableOnDataContext &= myExpr.isPhysical();
      }

      String enteredName = null;
      boolean replaceAllOccurrences = false;
      boolean delegate = false;
      if (isInplaceAvailableOnDataContext) {
        final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(myEditor);
        if (activeIntroducer == null) {

          myInplaceIntroduceParameterPopup =
            new InplaceIntroduceParameterPopup(myProject, myEditor, classMemberRefs,
                                               typeSelectorManager,
                                               myExpr, myLocalVar, method, methodToSearchFor, occurences,
                                               getParamsToRemove(method, occurences),
                                               mustBeFinal);
          if (myInplaceIntroduceParameterPopup.startInplaceIntroduceTemplate()) {
            return;
          }
        }
        else {
          AbstractInplaceIntroducer.stopIntroduce(myEditor);
          myExpr = (PsiExpression)activeIntroducer.getExpr();
          myLocalVar = (PsiLocalVariable)activeIntroducer.getLocalVariable();
          occurences = (PsiExpression[])activeIntroducer.getOccurrences();
          enteredName = activeIntroducer.getInputName();
          replaceAllOccurrences = activeIntroducer.isReplaceAllOccurrences();
          delegate = ((InplaceIntroduceParameterPopup)activeIntroducer).isGenerateDelegate();
        }
      }
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        @NonNls String parameterName = "anObject";
        boolean replaceAllOccurences = true;
        boolean isDeleteLocalVariable = true;
        PsiExpression initializer = myLocalVar != null && myExpr == null ? myLocalVar.getInitializer() : myExpr;
        new IntroduceParameterProcessor(myProject, method, methodToSearchFor, initializer, myExpr, myLocalVar, isDeleteLocalVariable, parameterName,
                                        replaceAllOccurences, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, mustBeFinal,
                                        false, null,
                                        getParamsToRemove(method, occurences)).run();
      } else {
        if (myEditor != null) {
          RefactoringUtil.highlightAllOccurences(myProject, occurences, myEditor);
        }
        final IntroduceParameterDialog dialog =
          new IntroduceParameterDialog(myProject, classMemberRefs, occurences, myLocalVar, myExpr,
                                       createNameSuggestionGenerator(myExpr, propName, myProject, enteredName),
                                       typeSelectorManager, methodToSearchFor, method, getParamsToRemove(method, occurences), mustBeFinal);
        dialog.setReplaceAllOccurrences(replaceAllOccurrences);
        dialog.setGenerateDelegate(delegate);
        dialog.show();
        if (myEditor != null) {
          myEditor.getSelectionModel().removeSelection();
        }
      }
    }

    private TIntArrayList getParamsToRemove(PsiMethod method, PsiExpression[] occurences) {
      PsiExpression expressionToRemoveParamFrom = myExpr;
      if (myExpr == null) {
        expressionToRemoveParamFrom = myLocalVar.getInitializer();
      }
      return expressionToRemoveParamFrom == null ? new TIntArrayList() : Util
        .findParametersToRemove(method, expressionToRemoveParamFrom, occurences);
    }
  }

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroduceParameterPopup;
  }
}
