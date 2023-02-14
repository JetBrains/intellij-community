// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.extractMethodObject;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class ExtractMethodObjectProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ExtractMethodObjectProcessor.class);

  private final PsiElementFactory myElementFactory;

  protected final MyExtractMethodProcessor myExtractProcessor;
  private boolean myCreateInnerClass = true;
  @NotNull
  private String myInnerClassName;

  private boolean myMultipleExitPoints;
  private PsiField[] myOutputFields;

  private PsiMethod myInnerMethod;
  private boolean myMadeStatic;
  private final Set<MethodToMoveUsageInfo> myUsages = new LinkedHashSet<>();
  private PsiClass myInnerClass;
  private boolean myChangeReturnType;
  private Runnable myCopyMethodToInner;
  private final UniqueNameGenerator myFieldNameGenerator = new UniqueNameGenerator();
  private String myResultFieldName = null;
  private final CodeStyleSettings myStyleSettings;

  private static final Key<Boolean> GENERATED_RETURN = new Key<>("GENERATED_RETURN");

  public ExtractMethodObjectProcessor(Project project, Editor editor, PsiElement[] elements, @NotNull String innerClassName) {
    super(project);
    myInnerClassName = innerClassName;
    myExtractProcessor = new MyExtractMethodProcessor(project, editor, elements, null,
                                                      JavaRefactoringBundle.message("extract.method.object"), innerClassName, HelpID.EXTRACT_METHOD_OBJECT);
    myElementFactory = JavaPsiFacade.getElementFactory(project);
    myStyleSettings = editor != null ? CodeStyle.getSettings(editor) :
                      CodeStyle.getSettings(elements[0].getContainingFile());
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo @NotNull [] usages) {
    return new ExtractMethodObjectViewDescriptor(getMethod());
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final ArrayList<UsageInfo> result = new ArrayList<>();
    final PsiClass containingClass = Objects.requireNonNull(getMethod().getContainingClass());
    final SearchScope scope = PsiUtilCore.getVirtualFile(containingClass) == null
                              ? new LocalSearchScope(containingClass)
                              : GlobalSearchScope.projectScope(myProject);
    PsiReference[] refs =
        ReferencesSearch.search(getMethod(), scope, false).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (element.isValid()) {
        result.add(new UsageInfo(element));
      }
    }
    if (isCreateInnerClass()) {
      final Set<PsiMethod> usedMethods = new LinkedHashSet<>();
      getMethod().accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          final PsiMethod method = expression.resolveMethod();
          if (method != null) {
            usedMethods.add(method);
          }
        }
      });


      for (PsiMethod usedMethod : usedMethods) {
        if (usedMethod.hasModifierProperty(PsiModifier.PRIVATE) &&
            (!usedMethod.hasModifierProperty(PsiModifier.STATIC) || myExtractProcessor.isStatic())) {
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
    UsageInfo[] usageInfos = result.toArray(UsageInfo.EMPTY_ARRAY);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  @Override
  public void performRefactoring(final UsageInfo @NotNull [] usages) {
    try {
      if (isCreateInnerClass()) {
        PsiClass containingClass = Objects.requireNonNull(getMethod().getContainingClass());
        myInnerClass = (PsiClass)addInnerClass(containingClass, myElementFactory.createClass(getInnerClassName()));
        final boolean isStatic = copyMethodModifiers() && notHasGeneratedFields();
        for (UsageInfo usage : usages) {
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(usage.getElement(), PsiMethodCallExpression.class);
          if (methodCallExpression != null) {
            replaceMethodCallExpression(inferTypeArguments(methodCallExpression), methodCallExpression);
          }
        }

        if (myExtractProcessor.generatesConditionalExit()) {
          myResultFieldName = uniqueFieldName(new String[]{"myResult"});
          myInnerClass.add(myElementFactory.createField(myResultFieldName, PsiTypes.booleanType()));
          myInnerClass.add(myElementFactory.createMethodFromText("boolean is(){return " + myResultFieldName + ";}", myInnerClass));
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
        myCopyMethodToInner = () -> {
          copyMethodWithoutParameters();
          copyMethodTypeParameters();
        };
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

  void moveUsedMethodsToInner() {
    if (!myUsages.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        WriteAction.run(() -> {
          for (MethodToMoveUsageInfo usage : myUsages) {
            final PsiMember member = (PsiMember)usage.getElement();
            LOG.assertTrue(member != null);
            myInnerClass.add(member.copy());
            member.delete();
          }
        });
        return;
      }
      final List<MemberInfo> memberInfos = new ArrayList<>();
      for (MethodToMoveUsageInfo usage : myUsages) {
        memberInfos.add(new MemberInfo((PsiMethod)usage.getElement()));
      }

      final MemberSelectionPanel panel = new MemberSelectionPanel(JavaRefactoringBundle.message("move.methods.panel.title"), memberInfos, null);
      DialogWrapper dlg = new DialogWrapper(myProject, false) {
        {
          init();
          setTitle(JavaRefactoringBundle.message("move.methods.used.in.extracted.block.only"));
        }


        @Override
        protected JComponent createCenterPanel() {
          return panel;
        }
      };
      if (dlg.showAndGet()) {
        WriteAction.run(() -> {
          for (MemberInfoBase<PsiMember> memberInfo : panel.getTable().getSelectedMemberInfos()) {
            if (memberInfo.isChecked()) {
              myInnerClass.add(memberInfo.getMember().copy());
              memberInfo.getMember().delete();
            }
          }
        });
      }
    }
  }

  private void addOutputVariableFieldsWithGetters() throws IncorrectOperationException {
    final Map<String, String> var2FieldNames = new HashMap<>();
    final PsiVariable[] outputVariables = myExtractProcessor.getOutputVariables();
    for (int i = 0; i < outputVariables.length; i++) {
      final PsiVariable var = outputVariables[i];
      final PsiField outputField = myOutputFields[i];
      final String name = getPureName(var);
      final PsiField field;
      if (outputField != null) {
        var2FieldNames.put(var.getName(), outputField.getName());
        myInnerClass.add(outputField);
        field = outputField;
      } else {
        field = PropertyUtilBase.findPropertyField(myInnerClass, name, false);
      }
      LOG.assertTrue(field != null, "i:" + i + "; output variables: " + Arrays.toString(outputVariables) + "; parameters: " + Arrays.toString(getMethod().getParameterList().getParameters()) + "; output field: " + outputField );
      myInnerClass.add(GenerateMembersUtil.generateGetterPrototype(field));
    }

    final PsiCodeBlock body = getMethod().getBody();
    LOG.assertTrue(body != null);
    final LinkedHashSet<PsiLocalVariable> vars = new LinkedHashSet<>();
    final Map<PsiElement, PsiElement> replacementMap = new LinkedHashMap<>();
    final List<PsiReturnStatement> returnStatements = new ArrayList<>();
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        returnStatements.add(statement);
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {}

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}
    });
    if (myExtractProcessor.generatesConditionalExit()) {
      for (int i = 0; i < returnStatements.size() - 1; i++) {
        final PsiReturnStatement condition = returnStatements.get(i);
        final PsiElement container = condition.getParent();
        LOG.assertTrue(myResultFieldName != null);
        final PsiStatement resultStmt = myElementFactory.createStatementFromText(myResultFieldName + " = true;", container);
        if (!CommonJavaRefactoringUtil.isLoopOrIf(container)) {
          container.addBefore(resultStmt, condition);
        } else {
          CommonJavaRefactoringUtil.putStatementInLoopBody(resultStmt, container, condition);
        }
      }

      LOG.assertTrue(!returnStatements.isEmpty());
      final PsiReturnStatement returnStatement = returnStatements.get(returnStatements.size() - 1);
      final PsiElement container = returnStatement.getParent();
      LOG.assertTrue(myResultFieldName != null);
      final PsiStatement resultStmt = myElementFactory.createStatementFromText(myResultFieldName + " = false;", container);
      if (!CommonJavaRefactoringUtil.isLoopOrIf(container)) {
        container.addBefore(resultStmt, returnStatement);
      } else {
        CommonJavaRefactoringUtil.putStatementInLoopBody(resultStmt, container, returnStatement);
      }
    }
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReturnStatement(final @NotNull PsiReturnStatement statement) {
        super.visitReturnStatement(statement);
        try {
          PsiStatement returnThisStatement = myElementFactory.createStatementFromText("return this;", statement);
          returnThisStatement.putCopyableUserData(GENERATED_RETURN, true);
          replacementMap.put(statement, returnThisStatement);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {}

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}

      @Override
      public void visitDeclarationStatement(final @NotNull PsiDeclarationStatement statement) {
        super.visitDeclarationStatement(statement);
        final PsiElement[] declaredElements = statement.getDeclaredElements();//todo
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiVariable) {
            for (PsiVariable variable : outputVariables) {
              PsiVariable var = (PsiVariable)declaredElement;
              if (Comparing.strEqual(var.getName(), variable.getName())) {
                replacementMap.put(var, var.getInitializer());
              }
            }
          }
        }
      }

      @Override
      public void visitParameter(@NotNull PsiParameter parameter) {
        super.visitParameter(parameter);
        final PsiElement declarationScope = parameter.getDeclarationScope();
        for (PsiVariable variable : outputVariables) {
          if (Comparing.strEqual(variable.getName(), parameter.getName())) {
            replacementMap.put(parameter, myElementFactory.createStatementFromText(myInnerClassName + ".this." + var2FieldNames.get(variable.getName()) + " = " + parameter.getName() + ";", declarationScope));
          }
        }
      }

      @Override
      public void visitReferenceExpression(final @NotNull PsiReferenceExpression expression) {
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

    for (PsiLocalVariable var : vars) {
      final String fieldName = var2FieldNames.get(var.getName());
      for (PsiReference reference : ReferencesSearch.search(var, var.getUseScope())) {
        reference.handleElementRename(fieldName);
      }
    }

    Map<PsiStatement, PsiForeachStatement> blocksToReplace = new LinkedHashMap<>();
    for (PsiElement statement : replacementMap.keySet()) {
      final PsiElement replacement = replacementMap.get(statement);
      if (replacement != null) {
        if (statement instanceof PsiParameter) {
          PsiCodeBlock codeBlock = null;
          final PsiElement declarationScope = ((PsiParameter)statement).getDeclarationScope();
          if (declarationScope instanceof PsiForeachStatement) {
            final PsiStatement loopBody = ((PsiForeachStatement)declarationScope).getBody();
            if (loopBody instanceof PsiBlockStatement) {
              codeBlock = ((PsiBlockStatement)loopBody).getCodeBlock();
            }
            else {
              blocksToReplace.put((PsiStatement)replacement, (PsiForeachStatement)declarationScope);
            }
          }
          else if (declarationScope instanceof PsiCatchSection){
            codeBlock = ((PsiCatchSection)declarationScope).getCatchBlock();
          }
          if (codeBlock != null) {
            codeBlock.addBefore(replacement, codeBlock.getFirstBodyElement());
          }
        }
        else if (statement instanceof PsiLocalVariable variable) {
          variable.normalizeDeclaration();
          final PsiExpression initializer = variable.getInitializer();
          LOG.assertTrue(initializer != null);
          final PsiStatement assignmentStatement = myElementFactory.createStatementFromText(var2FieldNames.get(variable.getName()) + " = " + initializer.getText() + ";", statement);
          final PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(statement, PsiDeclarationStatement.class);
          LOG.assertTrue(declaration != null);
          declaration.replace(assignmentStatement);
        }
        else {
          if (statement instanceof PsiReturnStatement) {
            final PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
            if (!(returnValue instanceof PsiReferenceExpression || returnValue == null || returnValue instanceof PsiLiteralExpression)) {
              statement.getParent()
                .addBefore(myElementFactory.createStatementFromText(returnValue.getText() + ";", returnValue), statement);
            }
          }
          statement.replace(replacement);
        }
      } else {
        statement.delete();
      }
    }

    for (PsiStatement statement : blocksToReplace.keySet()) {
      CommonJavaRefactoringUtil.putStatementInLoopBody(statement, blocksToReplace.get(statement), null);
    }

    myChangeReturnType = true;
  }

  void runChangeSignature() {
    if (myCopyMethodToInner != null) {
      myCopyMethodToInner.run();
    }
    if (myChangeReturnType) {
      final PsiTypeElement typeElement = ((PsiLocalVariable)((PsiDeclarationStatement)JavaPsiFacade.getElementFactory(myProject)
        .createStatementFromText(myInnerClassName + " l =null;", myInnerClass)).getDeclaredElements()[0]).getTypeElement();
      final PsiTypeElement innerMethodReturnTypeElement = myInnerMethod.getReturnTypeElement();
      LOG.assertTrue(innerMethodReturnTypeElement != null);
      innerMethodReturnTypeElement.replace(typeElement);
    }
  }

  @NotNull
  private String getPureName(@NotNull PsiVariable var) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
    return styleManager.variableNameToPropertyName(var.getName(), styleManager.getVariableKind(var));
  }

  public  PsiExpression processMethodDeclaration( PsiExpressionList expressionList) throws IncorrectOperationException {
    if (isCreateInnerClass()) {
      final String typeArguments = getMethod().hasTypeParameters() ? "<" + StringUtil.join(Arrays.asList(getMethod().getTypeParameters()),
                                                                                           typeParameter -> {
                                                                                             final String typeParameterName =
                                                                                               typeParameter.getName();
                                                                                             LOG.assertTrue(typeParameterName != null);
                                                                                             return typeParameterName;
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
      adjustTargetClassReferences(methodBody);
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
      newReplacement = argumentList.isEmpty() ? staticqualifier + "." :
                       "new " + staticqualifier + inferredTypeArguments + argumentList.getText() + ".";
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
      List<String> typeSignature = new ArrayList<>();
      final PsiSubstitutor substitutor = methodCallExpression.resolveMethodGenerics().getSubstitutor();
      for (final PsiTypeParameter typeParameter : methodTypeParameters) {
        final PsiType type = substitutor.substitute(typeParameter);
        if (!PsiTypesUtil.isDenotableType(type, methodCallExpression)) {
          return "";
        }
        typeSignature.add(type.getPresentableText());
      }
      return "<" + StringUtil.join(typeSignature, ", ") + ">";

    }
    return "";
  }

  @Override
  @NotNull
  protected String getCommandName() {
    return JavaRefactoringBundle.message("extract.method.object");
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
    if (isCreateInnerClass()) {
      adjustTargetClassReferences(methodBody);
    }
    replacedMethodBody.replace(methodBody);
    PsiUtil.setModifierProperty(newMethod, PsiModifier.STATIC, myInnerClass.hasModifierProperty(PsiModifier.STATIC) && notHasGeneratedFields());
    myInnerMethod = (PsiMethod)myInnerClass.add(newMethod);
  }

  private void adjustTargetClassReferences(final PsiElement body) throws IncorrectOperationException {
    PsiManager manager = PsiManager.getInstance(myProject);
    PsiClass targetClass = getMethod().getContainingClass();
    //Actually we should go into lambdas as this/super expressions inside them still refer to the outer class instance.
    //Visiting returns inside lambdas is safe as they never have GENERATED_RETURN inside
    //noinspection UnsafeReturnStatementVisitor
    body.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        if (statement.getCopyableUserData(GENERATED_RETURN) == null) { // do not modify our generated returns
          super.visitReturnStatement(statement);
        }
      }

      @Override
      public void visitThisExpression(@NotNull PsiThisExpression expression) {
        if (expression.getQualifier() == null) {
          expression.replace(RefactoringChangeUtil.createThisExpression(manager, targetClass));
        }
      }

      @Override
      public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
        if (expression.getQualifier() == null) {
          expression.replace(RefactoringChangeUtil.createSuperExpression(manager, targetClass));
        }
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        // do not visit sub classes
      }
    });
  }

  private boolean notHasGeneratedFields() {
    return !myMultipleExitPoints && getMethod().getParameterList().isEmpty();
  }

  private void createInnerClassConstructor(final PsiParameter[] parameters) throws IncorrectOperationException {
    final PsiMethod constructor = myElementFactory.createConstructor();
    final PsiParameterList parameterList = constructor.getParameterList();
    for (PsiParameter parameter : parameters) {
      final PsiModifierList parameterModifierList = parameter.getModifierList();
      LOG.assertTrue(parameterModifierList != null);
      final String parameterName = parameter.getName();
      PsiParameter parm = myElementFactory.createParameter(parameterName, parameter.getType());
      if (myStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS) {
        final PsiModifierList modifierList = parm.getModifierList();
        LOG.assertTrue(modifierList != null);
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
      parameterList.add(parm);

      final PsiField field = createField(parm, constructor, parameterModifierList.hasModifierProperty(PsiModifier.FINAL));
      for (PsiReference reference : ReferencesSearch.search(parameter, parameter.getUseScope())) {
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
      String fieldName = uniqueFieldName(styleManager.suggestVariableName(
        VariableKind.FIELD, styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER), null, type).names);
      PsiField field = myElementFactory.createField(fieldName, type);

      final PsiModifierList modifierList = field.getModifierList();
      LOG.assertTrue(modifierList != null);
      if (NullableNotNullManager.isNullable(parameter)) {
        final String annotationName = NullableNotNullManager.getInstance(myProject).getDefaultNullable();
        modifierList.addAfter(myElementFactory.createAnnotationFromText("@" + annotationName, field), null);
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

  @NotNull
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

  protected AbstractExtractDialog createExtractMethodObjectDialog(final MyExtractMethodProcessor processor) {
    return new ExtractMethodObjectDialog(myProject, processor.getTargetClass(), processor.getInputVariables(), processor.getReturnType(),
                                         processor.getTypeParameterList(),
                                         processor.getThrownExceptions(), processor.isStatic(), processor.isCanBeStatic(),
                                         processor.getElements(), myMultipleExitPoints){
      @Override
      protected boolean isUsedAfter(PsiVariable variable) {
        return ArrayUtil.find(processor.getOutputVariables(), variable) != -1;
      }
    };
  }

  protected PsiElement addInnerClass(PsiClass containingClass, PsiClass innerClass) {
    return containingClass.add(innerClass);
  }

  public PsiClass getInnerClass() {
    return myInnerClass;
  }

  protected boolean isFoldingApplicable() {
    return true;
  }

  private String uniqueFieldName(String[] candidates) {
    String name = ContainerUtil.find(candidates, myFieldNameGenerator::isUnique);
    if (name == null) {
      name = myFieldNameGenerator.generateUniqueName(candidates[0]);
    }
    myFieldNameGenerator.addExistingName(name);
    return name;
  }

  public class MyExtractMethodProcessor extends ExtractMethodProcessor {

    public MyExtractMethodProcessor(Project project,
                                    Editor editor,
                                    PsiElement[] elements,
                                    PsiType forcedReturnType,
                                    @NlsContexts.DialogTitle String refactoringName,
                                    String initialMethodName,
                                    String helpId) {
      super(project, editor, elements, forcedReturnType, refactoringName, initialMethodName, helpId);

    }

    @Override
    protected void initDuplicates(@Nullable Set<? extends TextRange> textRanges) {
      myDuplicates = Optional.ofNullable(getExactDuplicatesFinder())
                             .map(finder -> finder.findDuplicates(myTargetClass))
                             .orElse(new ArrayList<>());
    }

    @Override
    public boolean initParametrizedDuplicates(boolean showDialog) {
      return false;
    }

    @Override
    protected boolean insertNotNullCheckIfPossible() {
      return false;
    }

    @Override
    protected boolean isNeedToChangeCallContext() {
      return false;
    }

    @Override
    protected void apply(final AbstractExtractDialog dialog) {
      super.apply(dialog);
      myCreateInnerClass = !(dialog instanceof ExtractMethodObjectDialog) || ((ExtractMethodObjectDialog)dialog).createInnerClass();
      myInnerClassName = myCreateInnerClass ? StringUtil.capitalize(dialog.getChosenMethodName()) : dialog.getChosenMethodName();
    }

    @Override
    protected AbstractExtractDialog createExtractMethodDialog(final boolean direct) {
      return createExtractMethodObjectDialog(this);
    }

    @Override
    protected PsiExpression expressionToReplace(PsiExpression expression) {
      if (expression instanceof PsiUnaryExpression) {
        final IElementType elementType = ((PsiUnaryExpression)expression).getOperationTokenType();
        if (elementType == JavaTokenType.PLUSPLUS || elementType == JavaTokenType.MINUSMINUS) {
          PsiExpression operand = ((PsiUnaryExpression)expression).getOperand();
          if (operand != null) {
            return ((PsiBinaryExpression)expression.replace(
              myElementFactory.createExpressionFromText(operand.getText() + " + x", operand))).getROperand();
          }
        }
      }
      return super.expressionToReplace(expression);
    }

    @Override
    protected boolean checkOutputVariablesCount() {
      myMultipleExitPoints = super.checkOutputVariablesCount();
      myOutputFields = new PsiField[myOutputVariables.length];
      for (int i = 0; i < myOutputVariables.length; i++) {
        PsiVariable variable = myOutputVariables[i];
        if (!myInputVariables.contains(variable)) { //one field creation
          String fieldName = uniqueFieldName(JavaCodeStyleManager.getInstance(myProject)
            .suggestVariableName(VariableKind.FIELD, getPureName(variable), null, variable.getType()).names);
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
                                 CommonJavaRefactoringUtil.isInStaticContext(match.getMatchStart(), getExtractedMethod().getContainingClass()) &&
                                 !myInnerMethod.getContainingClass().hasModifierProperty(PsiModifier.STATIC);
      final PsiElement element = super.processMatch(match);
      if (makeStatic) {
        myMadeStatic = true;
        final PsiModifierList modifierList = myInnerMethod.getContainingClass().getModifierList();
        LOG.assertTrue(modifierList != null);
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
        PsiUtil.setModifierProperty(myInnerMethod, PsiModifier.STATIC, true);
      }
      PsiMethodCallExpression methodCallExpression = getMatchMethodCallExpression(element);
      if (methodCallExpression == null) return element;

      PsiExpression expression = processMethodDeclaration(methodCallExpression.getArgumentList());

      return methodCallExpression.replace(expression);
    }

    @Override
    protected void declareNecessaryVariablesAfterCall(final PsiVariable outputVariable) throws IncorrectOperationException {
      if (myMultipleExitPoints) {
        final String object = JavaCodeStyleManager.getInstance(myProject).suggestUniqueVariableName(StringUtil.decapitalize(myInnerClassName), outputVariable, true);
        final PsiStatement methodCallStatement = PsiTreeUtil.getParentOfType(getMethodCall(), PsiStatement.class);
        LOG.assertTrue(methodCallStatement != null);
        final PsiStatement declarationStatement = myElementFactory
          .createStatementFromText(myInnerClassName + " " + object + " = " + getMethodCall().getText() + ";", myInnerMethod);
        if (methodCallStatement instanceof PsiIfStatement) {
          methodCallStatement.getParent().addBefore(declarationStatement, methodCallStatement);
          final PsiExpression conditionExpression = ((PsiIfStatement)methodCallStatement).getCondition();
          setMethodCall((PsiMethodCallExpression)conditionExpression.replace(myElementFactory.createExpressionFromText(object + ".is()", myInnerMethod)));
        } else if (myElements[0] instanceof PsiExpression){
          methodCallStatement.getParent().addBefore(declarationStatement, methodCallStatement);
        } else {
          final PsiDeclarationStatement replace = (PsiDeclarationStatement)methodCallStatement.replace(declarationStatement);
          setMethodCall((PsiMethodCallExpression)((PsiLocalVariable)replace.getDeclaredElements()[0]).getInitializer());
        }

        PsiVariable[] usedVariables = myOutputVariables;
        if (generatesConditionalExit() && myOutputVariable != null && !myControlFlowWrapper.needVariableValueAfterEnd(myOutputVariable)) {
          usedVariables = ArrayUtil.remove(usedVariables, myOutputVariable);
        }
        Collection<ControlFlowUtil.VariableInfo> reassigned = myControlFlowWrapper.getInitializedTwice();
        for (PsiVariable variable : usedVariables) {
          String name = variable.getName();
          LOG.assertTrue(name != null);
          PsiStatement st = null;
          final String pureName = getPureName(variable);
          final int varIdxInOutput = ArrayUtil.find(myOutputVariables, variable);
          final String getterName = varIdxInOutput > -1 && myOutputFields[varIdxInOutput] != null
                                    ? GenerateMembersUtil.suggestGetterName(myOutputFields[varIdxInOutput])
                                    : GenerateMembersUtil.suggestGetterName(pureName, variable.getType(), myProject);
          if (isDeclaredInside(variable)) {
            st = myElementFactory.createStatementFromText(
              variable.getType().getCanonicalText() + " " + name + " = " + object + "." + getterName + "();",
              myInnerMethod);
            if (reassigned.contains(new ControlFlowUtil.VariableInfo(variable, null))) {
              final PsiElement[] psiElements = ((PsiDeclarationStatement)st).getDeclaredElements();
              assert psiElements.length > 0;
              PsiVariable var = (PsiVariable)psiElements[0];
              PsiUtil.setModifierProperty(var, PsiModifier.FINAL, false);
            }
          }
          else {
            if (varIdxInOutput != -1) {
              st = myElementFactory.createStatementFromText(name + " = " + object + "." + getterName + "();", myInnerMethod);
            }
          }
          if (st != null) {
            addToMethodCallLocation(st);
          }
        }
        if (myElements[0] instanceof PsiAssignmentExpression) {
          getMethodCall().getParent().replace(((PsiAssignmentExpression)getMethodCall().getParent()).getLExpression());
        } else if (myElements[0] instanceof PsiUnaryExpression) {
          getMethodCall().getParent().replace(((PsiBinaryExpression)getMethodCall().getParent()).getLOperand());
        }

        rebindExitStatement(object);
      }
      else {
        super.declareNecessaryVariablesAfterCall(outputVariable);
      }
    }

    @Override
    protected boolean isFoldingApplicable() {
      return ExtractMethodObjectProcessor.this.isFoldingApplicable();
    }

    private void rebindExitStatement(final String objectName) {
      final PsiStatement exitStatementCopy = myExtractProcessor.myFirstExitStatementCopy;
      if (exitStatementCopy != null) {
        myExtractProcessor.myDuplicates = new ArrayList<>();
        final Map<String, PsiVariable> outVarsNames = new HashMap<>();
        for (PsiVariable variable : myOutputVariables) {
          outVarsNames.put(variable.getName(), variable);
        }
        final Map<PsiElement, PsiElement> replaceMap = new HashMap<>();
        exitStatementCopy.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (expression.resolve() == null) {
              final PsiVariable variable = outVarsNames.get(expression.getReferenceName());
              if (variable != null) {
                final String call2Getter = objectName + "." + GenerateMembersUtil.suggestGetterName(getPureName(variable), variable.getType(),
                                                                                                    myProject) + "()";
                final PsiExpression callToGetter = myElementFactory.createExpressionFromText(call2Getter, variable);
                replaceMap.put(expression, callToGetter);
              }
            }
          }
        });
        for (PsiElement element : replaceMap.keySet()) {
          if (element.isValid()) {
            element.replace(replaceMap.get(element));
          }
        }
      }
    }

    public boolean generatesConditionalExit() {
      return myGenerateConditionalExit;
    }
  }
}
