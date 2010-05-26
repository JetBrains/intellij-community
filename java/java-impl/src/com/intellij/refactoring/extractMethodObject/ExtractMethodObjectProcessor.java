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
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.extractMethodObject;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class ExtractMethodObjectProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#" + com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor.class.getName());
  @NonNls public static final String REFACTORING_NAME = "Extract Method Object";

  private final PsiElementFactory myElementFactory;

  private final MyExtractMethodProcessor myExtractProcessor;
  private boolean myCreateInnerClass = true;
  private String myInnerClassName;

  private boolean myMultipleExitPoints;
  private PsiField[] myOutputFields;

  private PsiMethod myInnerMethod;
  private boolean myMadeStatic = false;
  private final Set<MethodToMoveUsageInfo> myUsages = new HashSet<MethodToMoveUsageInfo>();
  private PsiClass myInnerClass;

  public ExtractMethodObjectProcessor(Project project, Editor editor, PsiElement[] elements, final String innerClassName) {
    super(project);
    myInnerClassName = innerClassName;
    myExtractProcessor = new MyExtractMethodProcessor(project, editor, elements, null, REFACTORING_NAME, innerClassName, HelpID.EXTRACT_METHOD_OBJECT);
    myElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
  }

  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new ExtractMethodObjectViewDescriptor(getMethod());
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    PsiReference[] refs =
        ReferencesSearch.search(getMethod(), GlobalSearchScope.projectScope(myProject), false).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (element != null && element.isValid()) {
        result.add(new UsageInfo(element));
      }
    }
    if (isCreateInnerClass()) {
      final Set<PsiMethod> usedMethods = new HashSet<PsiMethod>();
      getMethod().accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          final PsiMethod method = expression.resolveMethod();
          if (method != null) {
            usedMethods.add(method);
          }
        }
      });


      for (PsiMethod usedMethod : usedMethods) {
        if (usedMethod.getModifierList().hasModifierProperty(PsiModifier.PRIVATE)) {
          PsiMethod toMove = usedMethod;
          for (PsiReference reference : ReferencesSearch.search(usedMethod)) {
            if (!PsiTreeUtil.isAncestor(getMethod(), reference.getElement(), false)) {
              toMove = null;
              break;
            }
          }
          if (toMove != null) {
            myUsages.add(new MethodToMoveUsageInfo(toMove));
          }
        }
      }
    }
    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(final PsiElement[] elements) {}

  protected void performRefactoring(final UsageInfo[] usages) {
    try {
      if (isCreateInnerClass()) {
        myInnerClass = (PsiClass)getMethod().getContainingClass().add(myElementFactory.createClass(getInnerClassName()));
        final boolean isStatic = copyMethodModifiers() && notHasGeneratedFields();
        for (UsageInfo usage : usages) {
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(usage.getElement(), PsiMethodCallExpression.class);
          if (methodCallExpression != null) {
            replaceMethodCallExpression(inferTypeArguments(methodCallExpression), methodCallExpression);
          }
        }

        if (myExtractProcessor.generatesConditionalExit()) {
          myInnerClass.add(myElementFactory.createField("myResult", PsiPrimitiveType.BOOLEAN));
          myInnerClass.add(myElementFactory.createMethodFromText("boolean is(){return myResult;}", myInnerClass));
        }

        final PsiParameter[] parameters = getMethod().getParameterList().getParameters();
        if (parameters.length > 0) {
          createInnerClassConstructor(parameters);
        } else if (isStatic) {
          final PsiMethod copy = (PsiMethod)getMethod().copy();
          copy.setName("invoke");
          myInnerClass.add(copy);
          if (myMultipleExitPoints) {
            addOutputVariableFieldsWithGetters();
          }
          return;
        }
        if (myMultipleExitPoints) {
          addOutputVariableFieldsWithGetters();
        }
        copyMethodWithoutParameters();
        copyMethodTypeParameters();
      } else {
        for (UsageInfo usage : usages) {
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(usage.getElement(), PsiMethodCallExpression.class);
          if (methodCallExpression != null) {
            methodCallExpression.replace(processMethodDeclaration( methodCallExpression.getArgumentList()));
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void moveUsedMethodsToInner() {
    if (!myUsages.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        for (MethodToMoveUsageInfo usage : myUsages) {
          final PsiMember member = (PsiMember)usage.getElement();
          LOG.assertTrue(member != null);
          myInnerClass.add(member.copy());
          member.delete();
        }
        return;
      }
      final List<MemberInfo> memberInfos = new ArrayList<MemberInfo>();
      for (MethodToMoveUsageInfo usage : myUsages) {
        memberInfos.add(new MemberInfo((PsiMethod)usage.getElement()));
      }

      final MemberSelectionPanel panel = new MemberSelectionPanel("Methods to move to the extracted class", memberInfos, null);
      DialogWrapper dlg = new DialogWrapper(myProject, false) {
        {
          init();
          setTitle("Move Methods Used in Extracted Block Only");
        }


        @Override
        protected JComponent createCenterPanel() {
          return panel;
        }
      };
      dlg.show();
      if (dlg.isOK()) {
        for (MemberInfoBase<PsiMember> memberInfo : panel.getTable().getSelectedMemberInfos()) {
          if (memberInfo.isChecked()) {
            myInnerClass.add(memberInfo.getMember().copy());
            memberInfo.getMember().delete();
          }

        }
      }
    }
  }

  private void addOutputVariableFieldsWithGetters() throws IncorrectOperationException {
    final Map<String, String> var2FieldNames = new HashMap<String, String>();
    final PsiVariable[] outputVariables = myExtractProcessor.getOutputVariables();
    for (int i = 0; i < outputVariables.length; i++) {
      final PsiVariable var = outputVariables[i];
      final PsiField outputField = myOutputFields[i];
      final String name = getPureName(var);
      LOG.assertTrue(name != null);
      if (outputField != null) {
        var2FieldNames.put(var.getName(), outputField.getName());
        myInnerClass.add(outputField);
      }
      final PsiField field = PropertyUtil.findPropertyField(myProject, myInnerClass, name, false);
      LOG.assertTrue(field != null, "i:" + i + "; output variables: " + Arrays.toString(outputVariables) + "; parameters: " + Arrays.toString(getMethod().getParameterList().getParameters()) + "; output field: " + outputField );
      myInnerClass.add(PropertyUtil.generateGetterPrototype(field));
    }

    PsiParameter[] params = getMethod().getParameterList().getParameters();
    ParameterInfoImpl[] infos = new ParameterInfoImpl[params.length];
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      infos[i] = new ParameterInfoImpl(i, param.getName(), param.getType());
    }
    ChangeSignatureProcessor cp = new ChangeSignatureProcessor(myProject, getMethod(), false, null, getMethod().getName(),
                                                               new PsiImmediateClassType(myInnerClass, PsiSubstitutor.EMPTY), infos);
    cp.run();
    final PsiCodeBlock body = getMethod().getBody();
    LOG.assertTrue(body != null);
    final List<PsiLocalVariable> vars = new ArrayList<PsiLocalVariable>();
    final Map<PsiElement, PsiElement> replacementMap = new LinkedHashMap<PsiElement, PsiElement>();
    final List<PsiReturnStatement> returnStatements = new ArrayList<PsiReturnStatement>();
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReturnStatement(final PsiReturnStatement statement) {
        super.visitReturnStatement(statement);
        try {
          returnStatements.add(statement);
          replacementMap.put(statement, myElementFactory.createStatementFromText("return this;", statement));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      @Override
      public void visitDeclarationStatement(final PsiDeclarationStatement statement) {
        super.visitDeclarationStatement(statement);
        final PsiElement[] declaredElements = statement.getDeclaredElements();//todo
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiVariable) {
            for (PsiVariable variable : outputVariables) {
              PsiLocalVariable var = (PsiLocalVariable)declaredElement;
              if (Comparing.strEqual(var.getName(), variable.getName())) {
                final PsiExpression initializer = var.getInitializer();
                if (initializer == null) {
                  replacementMap.put(statement, null);
                }
                else {
                  replacementMap.put(var, var);
                }
              }
            }
          }
        }
      }

      @Override
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiLocalVariable) {
          final String var = ((PsiLocalVariable)resolved).getName();
          for (PsiVariable variable : outputVariables) {
            if (Comparing.strEqual(variable.getName(), var)) {
              vars.add((PsiLocalVariable)resolved);
              break;
            }
          }
        }
      }
    });

    if (myExtractProcessor.generatesConditionalExit()) {
      for (int i = 0; i < returnStatements.size() - 1; i++) {
        final PsiReturnStatement condition = returnStatements.get(i);
        condition.getParent().addBefore(myElementFactory.createStatementFromText("myResult = true;", condition), condition);
      }

      LOG.assertTrue(!returnStatements.isEmpty());
      final PsiReturnStatement returnStatement = returnStatements.get(returnStatements.size() - 1);
      returnStatement.getParent().addBefore(myElementFactory.createStatementFromText("myResult = false;", returnStatement), returnStatement);
    }

    for (PsiLocalVariable var : vars) {
      final String fieldName = var2FieldNames.get(var.getName());
      for (PsiReference reference : ReferencesSearch.search(var)) {
        reference.handleElementRename(fieldName);
      }
    }

    for (PsiElement statement : replacementMap.keySet()) {
      final PsiElement replacement = replacementMap.get(statement);
      if (replacement != null) {
        if (statement instanceof PsiLocalVariable) {
          final PsiLocalVariable variable = (PsiLocalVariable)statement;
          variable.normalizeDeclaration();
          final PsiExpression initializer = variable.getInitializer();
          LOG.assertTrue(initializer != null);
          final PsiStatement assignmentStatement = myElementFactory.createStatementFromText(var2FieldNames.get(variable.getName()) + " = " + initializer.getText() + ";", statement);
          final PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(statement, PsiDeclarationStatement.class);
          LOG.assertTrue(declaration != null);
          declaration.replace(assignmentStatement);
        } else {
          statement.replace(replacement);
        }
      } else {
        statement.delete();
      }
    }
  }

  private String getPureName(PsiVariable var) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
    return var instanceof PsiLocalVariable
                        ? styleManager.variableNameToPropertyName(var.getName(), VariableKind.LOCAL_VARIABLE) : styleManager.variableNameToPropertyName(var.getName(), VariableKind.PARAMETER);
  }

  public  PsiExpression processMethodDeclaration( PsiExpressionList expressionList) throws IncorrectOperationException {
    if (isCreateInnerClass()) {
      final String typeArguments = getMethod().hasTypeParameters() ? "<" + StringUtil.join(Arrays.asList(getMethod().getTypeParameters()),
                                                                                           new Function<PsiTypeParameter, String>() {
                                                                                             public String fun(final PsiTypeParameter typeParameter) {
                                                                                               final String typeParameterName =
                                                                                                 typeParameter.getName();
                                                                                               LOG.assertTrue(typeParameterName != null);
                                                                                               return typeParameterName;
                                                                                             }
                                                                                           }, ", ") + ">" : "";
      final PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)myElementFactory.createExpressionFromText("invoke" + expressionList.getText(), null);
      return replaceMethodCallExpression(typeArguments, methodCallExpression);
    }
    else {
      final String paramsDeclaration = getMethod().getParameterList().getText();
      final PsiType returnType = getMethod().getReturnType();
      LOG.assertTrue(returnType != null);

      final PsiCodeBlock methodBody = getMethod().getBody();
      LOG.assertTrue(methodBody != null);
      return myElementFactory.createExpressionFromText("new Object(){ \n" +
                                                       "private " +
                                                       returnType.getPresentableText() +
                                                       " " + myInnerClassName +
                                                       paramsDeclaration +
                                                       methodBody.getText() +
                                                       "}." + myInnerClassName +
                                                       expressionList.getText(), null);
    }
  }


  private PsiMethodCallExpression replaceMethodCallExpression(final String inferredTypeArguments,
                                                              final PsiMethodCallExpression methodCallExpression)
      throws IncorrectOperationException {
    @NonNls final String staticqualifier =
      getMethod().hasModifierProperty(PsiModifier.STATIC) && notHasGeneratedFields() ? getInnerClassName() : null;
    @NonNls String newReplacement;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    if (staticqualifier != null) {
      newReplacement = argumentList.getExpressions().length > 0
                       ? "new " + staticqualifier + inferredTypeArguments + argumentList.getText() + "."
                       : staticqualifier + ".";
    } else {
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      final String qualifier = qualifierExpression != null ? qualifierExpression.getText() + "." : "";
      newReplacement = qualifier + "new " + getInnerClassName() + inferredTypeArguments + argumentList.getText()+ ".";
    }
    return (PsiMethodCallExpression)methodCallExpression.replace(myElementFactory.createExpressionFromText(newReplacement + "invoke()", null));
  }

  @NotNull
  private String inferTypeArguments(final PsiMethodCallExpression methodCallExpression) {
    final PsiReferenceParameterList list = methodCallExpression.getMethodExpression().getParameterList();

    if (list != null && list.getTypeArguments().length > 0) {
      return list.getText();
    }
    final PsiTypeParameter[] methodTypeParameters = getMethod().getTypeParameters();
    if (methodTypeParameters.length > 0) {
      List<String> typeSignature = new ArrayList<String>();
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(getMethod().getProject()).getResolveHelper();
      for (final PsiTypeParameter typeParameter : methodTypeParameters) {
        final PsiType type = resolveHelper.inferTypeForMethodTypeParameter(typeParameter, getMethod().getParameterList().getParameters(),
                                                                           methodCallExpression.getArgumentList().getExpressions(),
                                                                           PsiSubstitutor.EMPTY, methodCallExpression, false);
        if (type == null || PsiType.NULL.equals(type)) {
          return "";
        }
        typeSignature.add(type.getPresentableText());
      }
      return "<" + StringUtil.join(typeSignature, ", ") + ">";

    }
    return "";
  }

  protected String getCommandName() {
    return REFACTORING_NAME;
  }


  private boolean copyMethodModifiers() throws IncorrectOperationException {
    final PsiModifierList methodModifierList = getMethod().getModifierList();

    final PsiModifierList innerClassModifierList = myInnerClass.getModifierList();
    LOG.assertTrue(innerClassModifierList != null);
    innerClassModifierList.setModifierProperty(VisibilityUtil.getVisibilityModifier(methodModifierList), true);
    final boolean isStatic = methodModifierList.hasModifierProperty(PsiModifier.STATIC);
    innerClassModifierList.setModifierProperty(PsiModifier.STATIC, isStatic);
    return isStatic;
  }

  private void copyMethodTypeParameters() throws IncorrectOperationException {
    final PsiTypeParameterList typeParameterList = myInnerClass.getTypeParameterList();
    LOG.assertTrue(typeParameterList != null);

    for (PsiTypeParameter parameter : getMethod().getTypeParameters()) {
      typeParameterList.add(parameter);
    }
  }

  private void copyMethodWithoutParameters() throws IncorrectOperationException {
    final PsiMethod newMethod = myElementFactory.createMethod("invoke", getMethod().getReturnType());
    newMethod.getThrowsList().replace(getMethod().getThrowsList());

    final PsiCodeBlock replacedMethodBody = newMethod.getBody();
    LOG.assertTrue(replacedMethodBody != null);
    final PsiCodeBlock methodBody = getMethod().getBody();
    LOG.assertTrue(methodBody != null);
    replacedMethodBody.replace(methodBody);
    PsiUtil.setModifierProperty(newMethod, PsiModifier.STATIC, myInnerClass.hasModifierProperty(PsiModifier.STATIC) && notHasGeneratedFields());
    myInnerMethod = (PsiMethod)myInnerClass.add(newMethod);
  }

  private boolean notHasGeneratedFields() {
    return !myMultipleExitPoints && getMethod().getParameterList().getParametersCount() == 0;
  }

  private void createInnerClassConstructor(final PsiParameter[] parameters) throws IncorrectOperationException {
    final PsiMethod constructor = myElementFactory.createConstructor();
    final PsiParameterList parameterList = constructor.getParameterList();
    for (PsiParameter parameter : parameters) {
      final PsiModifierList parameterModifierList = parameter.getModifierList();
      LOG.assertTrue(parameterModifierList != null);
      final String parameterName = parameter.getName();
      LOG.assertTrue(parameterName != null);
      PsiParameter parm = myElementFactory.createParameter(parameterName, parameter.getType());
      if (CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS) {
        final PsiModifierList modifierList = parm.getModifierList();
        LOG.assertTrue(modifierList != null);
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
      parameterList.add(parm);

      final PsiField field = createField(parm, constructor, parameterModifierList.hasModifierProperty(PsiModifier.FINAL));
      for (PsiReference reference : ReferencesSearch.search(parameter)) {
        reference.handleElementRename(field.getName());
      }
    }
    myInnerClass.add(constructor);
  }

  private PsiField createField(PsiParameter parameter, PsiMethod constructor, boolean isFinal) {
    final String parameterName = parameter.getName();
    PsiType type = parameter.getType();
    if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
    try {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(getMethod().getProject());
      final String fieldName = styleManager.suggestVariableName(VariableKind.FIELD, styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER), null, type).names[0];
      PsiField field = myElementFactory.createField(fieldName, type);

      final PsiModifierList modifierList = field.getModifierList();
      LOG.assertTrue(modifierList != null);
      if (AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NULLABLE, false)) {
        modifierList.addAfter(myElementFactory.createAnnotationFromText("@" + AnnotationUtil.NULLABLE, field), null);
      }
      modifierList.setModifierProperty(PsiModifier.FINAL, isFinal);

      final PsiCodeBlock methodBody = constructor.getBody();

      LOG.assertTrue(methodBody != null);

      @NonNls final  String stmtText;
      if (Comparing.strEqual(parameterName, fieldName)) {
        stmtText = "this." + fieldName + " = " + parameterName + ";";
      } else {
        stmtText = fieldName + " = " + parameterName + ";";
      }
      PsiStatement assignmentStmt = myElementFactory.createStatementFromText(stmtText, methodBody);
      assignmentStmt = (PsiStatement)CodeStyleManager.getInstance(constructor.getProject()).reformat(assignmentStmt);
      methodBody.add(assignmentStmt);

      field = (PsiField)myInnerClass.add(field);
      return field;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  protected void changeInstanceAccess(final Project project)
      throws IncorrectOperationException {
    if (myMadeStatic) {
      PsiReference[] refs =
          ReferencesSearch.search(myInnerMethod, GlobalSearchScope.projectScope(project), false).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference ref : refs) {
        final PsiElement element = ref.getElement();
        final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (callExpression != null) {
          replaceMethodCallExpression(inferTypeArguments(callExpression), callExpression);
        }
      }
    }
  }

  public PsiMethod getMethod() {
    return myExtractProcessor.getExtractedMethod();
  }

  public String getInnerClassName() {
    return myInnerClassName;
  }

  public void setCreateInnerClass(final boolean createInnerClass) {
    myCreateInnerClass = createInnerClass;
  }

  public boolean isCreateInnerClass() {
    return myCreateInnerClass;
  }


  public MyExtractMethodProcessor getExtractProcessor() {
    return myExtractProcessor;
  }

  public class MyExtractMethodProcessor extends ExtractMethodProcessor {

    public MyExtractMethodProcessor(Project project,
                                        Editor editor,
                                        PsiElement[] elements,
                                        PsiType forcedReturnType,
                                        String refactoringName,
                                        String initialMethodName,
                                        String helpId) {
      super(project, editor, elements, forcedReturnType, refactoringName, initialMethodName, helpId);

    }

    @Override
    protected void apply(final AbstractExtractDialog dialog) {
      super.apply(dialog);
      myCreateInnerClass = ((ExtractMethodObjectDialog)dialog).createInnerClass();
      myInnerClassName = myCreateInnerClass ? StringUtil.capitalize(dialog.getChosenMethodName()) : dialog.getChosenMethodName();
    }

    @Override
    protected AbstractExtractDialog createExtractMethodDialog(final boolean direct) {
      return new ExtractMethodObjectDialog(myProject, myTargetClass, myInputVariables, myReturnType, myTypeParameterList,
                                           myThrownExceptions, myStatic, myCanBeStatic, myElements, myMultipleExitPoints){
        @Override
        protected boolean isUsedAfter(PsiVariable variable) {
          return ArrayUtil.find(myOutputVariables, variable) != -1;
        }
      };
    }

    @Override
    protected boolean checkOutputVariablesCount() {
      myMultipleExitPoints = super.checkOutputVariablesCount();
      myOutputFields = new PsiField[myOutputVariables.length];
      for (int i = 0; i < myOutputVariables.length; i++) {
        PsiVariable variable = myOutputVariables[i];
        if (!myInputVariables.contains(variable)) { //one field creation
          final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
          final String fieldName =
            styleManager.suggestVariableName(VariableKind.FIELD, getPureName(variable), null, variable.getType()).names[0];
          try {
            myOutputFields[i] = myElementFactory.createField(fieldName, variable.getType());
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      return !myCreateInnerClass && myMultipleExitPoints;
    }

    @Override
    public PsiElement processMatch(final Match match) throws IncorrectOperationException {
      final boolean makeStatic = myInnerMethod != null &&
                                 RefactoringUtil.isInStaticContext(match.getMatchStart(), getExtractedMethod().getContainingClass());
      final PsiElement element = super.processMatch(match);
      if (makeStatic) {
        myMadeStatic = true;
        final PsiModifierList modifierList = myInnerMethod.getContainingClass().getModifierList();
        LOG.assertTrue(modifierList != null);
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
        PsiUtil.setModifierProperty(myInnerMethod, PsiModifier.STATIC, true);
      }
      PsiMethodCallExpression methodCallExpression = null;
      if (element instanceof PsiMethodCallExpression) {
        methodCallExpression = (PsiMethodCallExpression)element;
      }
      else if (element instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          methodCallExpression = (PsiMethodCallExpression)expression;
        }
        else if (expression instanceof PsiAssignmentExpression) {
          final PsiExpression psiExpression = ((PsiAssignmentExpression)expression).getRExpression();
          if (psiExpression instanceof PsiMethodCallExpression) {
            methodCallExpression = (PsiMethodCallExpression)psiExpression;
          }
        }
      } else if (element instanceof PsiDeclarationStatement) {
        final PsiElement[] declaredElements = ((PsiDeclarationStatement)element).getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable) {
            final PsiExpression initializer = ((PsiLocalVariable)declaredElement).getInitializer();
            if (initializer instanceof PsiMethodCallExpression) {
              methodCallExpression = (PsiMethodCallExpression)initializer;
              break;
            }
          }
        }
      }
      if (methodCallExpression == null) return element;

      PsiExpression expression = processMethodDeclaration(methodCallExpression.getArgumentList());

      return methodCallExpression.replace(expression);
    }

    public PsiVariable[] getOutputVariables() {
      return myOutputVariables;
    }

    @Override
    protected void declareNecessaryVariablesAfterCall(final PsiVariable outputVariable) throws IncorrectOperationException {
      if (myMultipleExitPoints) {
        final String object = StringUtil.decapitalize(myInnerClassName);
        final PsiStatement methodCallStatement = PsiTreeUtil.getParentOfType(getMethodCall(), PsiStatement.class);
        LOG.assertTrue(methodCallStatement != null);
        final PsiStatement declarationStatement = myElementFactory
          .createStatementFromText(myInnerClassName + " " + object + " = " + getMethodCall().getText() + ";", myInnerMethod);
        if (methodCallStatement instanceof PsiIfStatement) {
          methodCallStatement.getParent().addBefore(declarationStatement, methodCallStatement);
          final PsiExpression conditionExpression = ((PsiIfStatement)methodCallStatement).getCondition();
          conditionExpression.replace(myElementFactory.createExpressionFromText(object + ".is()", myInnerMethod));
        } else {
          methodCallStatement.replace(declarationStatement);
        }

        final List<PsiVariable> usedVariables = myControlFlowWrapper.getUsedVariables();
        Collection<ControlFlowUtil.VariableInfo> reassigned = myControlFlowWrapper.getInitializedTwice();
        for (PsiVariable variable : usedVariables) {
          String name = variable.getName();
          LOG.assertTrue(name != null);
          PsiStatement st = null;
          if (isDeclaredInside(variable)) {
            st = myElementFactory.createStatementFromText(
              variable.getType().getCanonicalText() + " " + name + " = " + object + "." + PropertyUtil.suggestGetterName(getPureName(variable), variable.getType()) + "();",
              myInnerMethod);
            if (reassigned.contains(new ControlFlowUtil.VariableInfo(variable, null))) {
              final PsiElement[] psiElements = ((PsiDeclarationStatement)st).getDeclaredElements();
              assert psiElements.length > 0;
              PsiVariable var = (PsiVariable)psiElements[0];
              PsiUtil.setModifierProperty(var, PsiModifier.FINAL, false);
            }
          }
          else {
            if (ArrayUtil.find(myOutputVariables, variable) != -1) {
              st = myElementFactory.createStatementFromText(name + " = " + object + "." + PropertyUtil.suggestGetterName(getPureName(variable), variable.getType()) + "();", myInnerMethod);
            }
          }
          if (st != null) {
            addToMethodCallLocation(st);
          }
        }
      }
      else {
        super.declareNecessaryVariablesAfterCall(outputVariable);
      }
    }

    public boolean generatesConditionalExit() {
      return myGenerateConditionalExit;
    }
  }
}
