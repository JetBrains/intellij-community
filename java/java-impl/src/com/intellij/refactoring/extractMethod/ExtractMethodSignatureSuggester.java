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
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.processing.HighlightMode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.ui.JBUI;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ExtractMethodSignatureSuggester {
  private static final Logger LOG = Logger.getInstance("#" + ExtractMethodSignatureSuggester.class.getName());
  private static final TObjectHashingStrategy<PsiExpression> ourEquivalenceStrategy = new TObjectHashingStrategy<PsiExpression>() {
    @Override
    public int computeHashCode(PsiExpression object) {
      return RefactoringUtil.unparenthesizeExpression(object).getClass().hashCode();
    }

    @Override
    public boolean equals(PsiExpression o1, PsiExpression o2) {
      return JavaPsiEquivalenceUtil
        .areExpressionsEquivalent(RefactoringUtil.unparenthesizeExpression(o1), RefactoringUtil.unparenthesizeExpression(o2));
    }
  };

  private final Project myProject;
  private final PsiElementFactory myElementFactory;

  private PsiMethod myExtractedMethod;
  private PsiMethodCallExpression myMethodCall;
  private VariableData[] myVariableData;

  public ExtractMethodSignatureSuggester(Project project,
                                         PsiMethod extractedMethod,
                                         PsiMethodCallExpression methodCall,
                                         VariableData[] variableDatum) {
    myProject = project;
    myElementFactory = JavaPsiFacade.getElementFactory(project);

    final PsiClass containingClass = extractedMethod.getContainingClass();
    LOG.assertTrue(containingClass != null);
    myExtractedMethod = myElementFactory.createMethodFromText(extractedMethod.getText(), containingClass.getLBrace());
    myMethodCall = methodCall;
    myVariableData = variableDatum;
  }

  public List<Match> getDuplicates(final PsiMethod method, final PsiMethodCallExpression methodCall, ParametersFolder folder) {
    final List<Match> duplicates = findDuplicatesSignature(method, folder);
    if (duplicates != null && !duplicates.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode() || 
          new PreviewDialog(method, myExtractedMethod, methodCall, myMethodCall, duplicates.size()).showAndGet()) {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        WriteCommandAction.runWriteCommandAction(myProject, () -> {
          myMethodCall = (PsiMethodCallExpression)methodCall.replace(myMethodCall);
          myExtractedMethod = (PsiMethod)method.replace(myExtractedMethod);
        });

        final DuplicatesFinder finder = MethodDuplicatesHandler.createDuplicatesFinder(myExtractedMethod);
        if (finder != null) {
          final List<VariableData> datas = finder.getParameters().getInputVariables();
          myVariableData = datas.toArray(new VariableData[datas.size()]);
          return finder.findDuplicates(myExtractedMethod.getContainingClass());
        }
      }
    }
    return null;
  }


  public PsiMethod getExtractedMethod() {
    return myExtractedMethod;
  }

  public PsiMethodCallExpression getMethodCall() {
    return myMethodCall;
  }

  public VariableData[] getVariableData() {
    return myVariableData;
  }

  @Nullable
  public List<Match> findDuplicatesSignature(final PsiMethod method, ParametersFolder folder) {
    final List<PsiExpression> copies = new ArrayList<>();
    final InputVariables variables = detectTopLevelExpressionsToReplaceWithParameters(copies);
    if (variables == null) {
      return null;
    }

    final DuplicatesFinder defaultFinder = MethodDuplicatesHandler.createDuplicatesFinder(myExtractedMethod);
    if (defaultFinder == null) {
      return null; 
    }

    final DuplicatesFinder finder = new DuplicatesFinder(defaultFinder.getPattern(), variables, defaultFinder.getReturnValue(),
                                                         new ArrayList<>()) {
      @Override
      protected boolean isSelf(PsiElement candidate) {
        return PsiTreeUtil.isAncestor(method, candidate, true);
      }
    };
    List<Match> duplicates = finder.findDuplicates(method.getContainingClass());

    if (duplicates != null && !duplicates.isEmpty()) {
      restoreRenamedParams(copies, folder);
      if (!myMethodCall.isValid()) {
        return null;
      }
      myMethodCall = (PsiMethodCallExpression)myMethodCall.copy();
      inlineSameArguments(method, copies, variables, duplicates);
      for (PsiExpression expression : copies) {
        myMethodCall.getArgumentList().add(expression);
      }
      return duplicates;
    }
    else {
      return null;
    }
  }

  private void inlineSameArguments(PsiMethod method, List<PsiExpression> copies, InputVariables variables, List<Match> duplicates) {
    final List<VariableData> variableDatum = variables.getInputVariables();
    final Map<PsiVariable, PsiExpression> toInline = new HashMap<>();
    final int strongParamsCound = method.getParameterList().getParametersCount();
    for (int i = strongParamsCound; i < variableDatum.size(); i++) {
      VariableData variableData = variableDatum.get(i);
      final THashSet<PsiExpression> map = new THashSet<>(ourEquivalenceStrategy);
      if (!collectParamValues(duplicates, variableData, map)) {
        continue;
      }

      final PsiExpression currentExpression = copies.get(i - strongParamsCound);
      map.add(currentExpression);

      if (map.size() == 1) {
        toInline.put(variableData.variable, currentExpression);
      }
    }

    if (!toInline.isEmpty()) {
      copies.removeAll(toInline.values());
      inlineArgumentsInMethodBody(toInline);
      removeRedundantParametersFromMethodSignature(toInline);
    }

    removeUnusedStongParams(strongParamsCound);
  }

  private void removeUnusedStongParams(int strongParamsCound) {
    final PsiExpression[] expressions = myMethodCall.getArgumentList().getExpressions();
    final PsiParameter[] parameters = myExtractedMethod.getParameterList().getParameters();
    final PsiCodeBlock body = myExtractedMethod.getBody();
    if (body != null) {
      final LocalSearchScope scope = new LocalSearchScope(body);
      for(int i = strongParamsCound - 1; i >= 0; i--) {
        final PsiParameter parameter = parameters[i];
        if (ReferencesSearch.search(parameter, scope).findFirst() == null) {
          parameter.delete();
          expressions[i].delete();
        }
      }
    }
  }

  private void removeRedundantParametersFromMethodSignature(Map<PsiVariable, PsiExpression> param2ExprMap) {
    for (PsiParameter parameter : myExtractedMethod.getParameterList().getParameters()) {
      if (param2ExprMap.containsKey(parameter)) {
        parameter.delete();
      }
    }
  }

  private void inlineArgumentsInMethodBody(final Map<PsiVariable, PsiExpression> param2ExprMap) {
    final Map<PsiExpression, PsiExpression> replacement = new HashMap<>();
    myExtractedMethod.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiVariable) {
          final PsiExpression toInlineExpr = param2ExprMap.get((PsiVariable)resolve);
          if (toInlineExpr != null) {
            replacement.put(expression, toInlineExpr);
          }
        }
      }
    });
    for (PsiExpression expression : replacement.keySet()) {
      expression.replace(replacement.get(expression));
    }
  }

  private static boolean collectParamValues(List<Match> duplicates, VariableData variableData, THashSet<PsiExpression> map) {
    for (Match duplicate : duplicates) {
      final List<PsiElement> values = duplicate.getParameterValues(variableData.variable);
      if (values == null || values.isEmpty()) {
        return false;
      }
      boolean found = false;
      for (PsiElement value : values) {
        if (value instanceof PsiExpression) {
          map.add((PsiExpression)value);
          found = true;
          break;
        }
      }
      if (!found) return false;
    }
    return true;
  }

  private void restoreRenamedParams(List<PsiExpression> copies, ParametersFolder folder) {
    final Map<String, String> renameMap = new HashMap<>();
    for (VariableData data : myVariableData) {
      final String replacement = folder.getGeneratedCallArgument(data);
      if (!data.name.equals(replacement)) {
        renameMap.put(data.name, replacement);
      }
    }

    if (!renameMap.isEmpty()) {
      for (PsiExpression currentExpression : copies) {
        final Map<PsiReferenceExpression, String> params = new HashMap<>();
        currentExpression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement resolve = expression.resolve();
            if (resolve instanceof PsiParameter && myExtractedMethod.equals(((PsiParameter)resolve).getDeclarationScope())) {
              final String name = ((PsiParameter)resolve).getName();
              final String variable = renameMap.get(name);
              if (renameMap.containsKey(name)) {
                params.put(expression, variable);
              }
            }
          }
        });
        for (PsiReferenceExpression expression : params.keySet()) {
          final String var = params.get(expression);
          expression.replace(myElementFactory.createExpressionFromText(var, expression));
        }
      }
    }
  }


  @Nullable
  private InputVariables detectTopLevelExpressionsToReplaceWithParameters(List<PsiExpression> copies) {
    final PsiParameter[] parameters = myExtractedMethod.getParameterList().getParameters();
    final List<PsiVariable> inputVariables = new ArrayList<>(Arrays.asList(parameters));
    final PsiCodeBlock body = myExtractedMethod.getBody();
    LOG.assertTrue(body != null);
    final PsiStatement[] pattern = body.getStatements();
    final List<PsiExpression> exprs = new ArrayList<>();
    for (PsiStatement statement : pattern) {
      if (statement instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
        if (expression instanceof PsiIfStatement || expression instanceof PsiLoopStatement) {
          continue;
        }
      }
      statement.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitCallExpression(PsiCallExpression callExpression) {
          final PsiExpressionList list = callExpression.getArgumentList();
          if (list != null) {
            for (PsiExpression expression : list.getExpressions()) {
              if (expression instanceof PsiReferenceExpression) {
                final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
                if (resolve instanceof PsiField) {
                  exprs.add(expression);
                }
              } else {
                exprs.add(expression);
              }
            }
          }
        }
      });
    }

    if (exprs.isEmpty()) {
      return null;
    }

    final UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator();
    for (PsiParameter parameter : parameters) {
      uniqueNameGenerator.addExistingName(parameter.getName());
    }

    SyntaxTraverser.psiTraverser().withRoot(myExtractedMethod.getBody())
      .filter(element -> element instanceof PsiVariable)
      .forEach(element -> uniqueNameGenerator.addExistingName(((PsiVariable)element).getName()));

    
    final THashMap<PsiExpression, String> unique = new THashMap<>(ourEquivalenceStrategy);
    final Map<PsiExpression, String> replacement = new HashMap<>();
    for (PsiExpression expr : exprs) {
      String name = unique.get(expr);
      if (name == null) {

        final PsiType type = GenericsUtil.getVariableTypeByExpressionType(expr.getType());
        if (type == null ||
            type == PsiType.NULL ||
            PsiUtil.resolveClassInType(type) instanceof PsiAnonymousClass ||
            LambdaUtil.notInferredType(type)) return null;

        copies.add(myElementFactory.createExpressionFromText(expr.getText(), body));

        final SuggestedNameInfo info = JavaCodeStyleManager.getInstance(myProject).suggestVariableName(VariableKind.PARAMETER, null, expr, null);
        final String paramName = info.names.length > 0 ? info.names[0] : "p";
        name = uniqueNameGenerator.generateUniqueName(paramName);

        final PsiParameter parameter = (PsiParameter)myExtractedMethod.getParameterList().add(myElementFactory.createParameter(name, type));
        inputVariables.add(parameter);
        unique.put(expr, name);
      }
      replacement.put(expr, name);
    }

    for (PsiExpression expression : replacement.keySet()) {
      expression.replace(myElementFactory.createExpressionFromText(replacement.get(expression), null));
    }

    return new InputVariables(inputVariables, myExtractedMethod.getProject(), new LocalSearchScope(myExtractedMethod), false);
  }

  private static class PreviewDialog extends DialogWrapper {
    private final PsiMethod myOldMethod;
    private final PsiMethod myNewMethod;
    private final PsiMethodCallExpression myOldCall;
    private final PsiMethodCallExpression myNewCall;
    private final int myDuplicatesNumber;

    public PreviewDialog(PsiMethod oldMethod,
                         PsiMethod newMethod,
                         PsiMethodCallExpression oldMethodCall,
                         PsiMethodCallExpression newMethodCall,
                         int duplicatesNumber) {
      super(oldMethod.getProject());
      myOldMethod = oldMethod;
      myNewMethod = newMethod;
      myOldCall = oldMethodCall;
      myNewCall = newMethodCall;
      myDuplicatesNumber = duplicatesNumber;
      setTitle("Extract Parameters to Replace Duplicates");
      setOKButtonText("Accept Signature Change");
      setCancelButtonText("Keep Original Signature");
      init();
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      return new JLabel("<html><b>No exact method duplicates were found</b>, though changed method as shown below has " + myDuplicatesNumber + " duplicate" + (myDuplicatesNumber > 1 ? "s" : "") + " </html>");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      final Project project = myOldMethod.getProject();
      final DiffPanelEx diffPanel = (DiffPanelEx)DiffManager.getInstance().createDiffPanel(null, project, getDisposable(), null);
      diffPanel.setComparisonPolicy(ComparisonPolicy.IGNORE_SPACE);
      diffPanel.setHighlightMode(HighlightMode.BY_WORD);
      DiffPanelOptions diffPanelOptions = diffPanel.getOptions();
      diffPanelOptions.setShowSourcePolicy(DiffPanelOptions.ShowSourcePolicy.OPEN_EDITOR);
      diffPanelOptions.setRequestFocusOnNewContent(false);
      SimpleDiffRequest request = new SimpleDiffRequest(project, null);
      final String oldContent = myOldMethod.getText() + "\n\n\nmethod call:\n " + myOldCall.getText();
      final String newContent = myNewMethod.getText() + "\n\n\nmethod call:\n " + myNewCall.getText();
      request.setContents(new SimpleContent(oldContent), new SimpleContent(newContent));
      request.setContentTitles("Before", "After");
      diffPanel.setDiffRequest(request);
      
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(diffPanel.getComponent(), BorderLayout.CENTER);
      panel.setBorder(IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(5)));
      return panel;
    }
  }
}
