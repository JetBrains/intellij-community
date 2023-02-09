// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VariableAccessFromInnerClassFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(VariableAccessFromInnerClassFix.class);
  private final PsiVariable myVariable;
  private final PsiElement myContext;
  @FixType
  private final int myFixType;
  private static final int MAKE_FINAL = 0;
  private static final int COPY_TO_FINAL = 1;
  private static final int MAKE_ARRAY = 2;
  private static final int UNKNOWN = -1;
  @MagicConstant(intValues = {MAKE_FINAL, COPY_TO_FINAL, MAKE_ARRAY, UNKNOWN})
  @interface FixType { }
  private static final Key<Map<PsiVariable,Boolean>>[] VARS = new Key[] {Key.create("VARS_TO_MAKE_FINAL"), Key.create("VARS_TO_TRANSFORM"), Key.create("???")};

  public VariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiElement element) {
    myVariable = variable;
    myContext = element;
    myFixType = getQuickFixType(variable);
    if (myFixType == UNKNOWN) return;

    getVariablesToFix().add(variable);
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiElement context = PsiTreeUtil.findSameElementInCopy(myContext, target);
    VariableAccessFromInnerClassFix fix = new VariableAccessFromInnerClassFix(
      PsiTreeUtil.findSameElementInCopy(myVariable, target), context);
    Collection<PsiVariable> targetVars = fix.getVariablesToFix();
    for (PsiVariable sourceVar : getVariablesToFix()) {
      targetVars.add(PsiTreeUtil.findSameElementInCopy(sourceVar, target));
    }
    return fix;
  }

  @Override
  @NotNull
  public String getText() {
    return switch (myFixType) {
      case MAKE_FINAL -> {
        Collection<PsiVariable> vars = getVariablesToFix();
        yield JavaBundle.message("intention.name.make.variable.final", myVariable.getName(), vars.size() == 1 ? 0 : 1);
      }
      case MAKE_ARRAY -> {
        Collection<PsiVariable> vars = getVariablesToFix();
        yield JavaBundle.message("intention.name.transform.variables.into.final.one.element.array", myVariable.getName(),
                                 vars.size() == 1 ? 0 : 1);
      }
      case COPY_TO_FINAL -> JavaBundle.message("intention.name.copy.to.final.temp.variable", myVariable.getName(),
                               !PsiUtil.isLanguageLevel8OrHigher(myContext) ? 0 : 1);
      default -> "";
    };
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("make.final.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myContext.isValid() &&
           BaseIntentionAction.canModify(myContext) &&
           myVariable.isValid() &&
           myFixType != UNKNOWN &&
           !getVariablesToFix().isEmpty() &&
           !inOwnInitializer(myVariable, myContext);
  }

  private static boolean inOwnInitializer(PsiVariable variable, PsiElement context) {
    return PsiTreeUtil.isAncestor(variable, context, false);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    try {
      switch (myFixType) {
        case MAKE_FINAL -> makeFinal();
        case MAKE_ARRAY -> makeArray();
        case COPY_TO_FINAL -> copyToFinal(myVariable, myContext);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      getVariablesToFix().clear();
    }
  }

  private void makeArray() {
    for (PsiVariable var : getVariablesToFix()) {
      makeArray(var, myContext);
    }
  }

  @NotNull
  private Collection<PsiVariable> getVariablesToFix() {
    Map<PsiVariable, Boolean> vars = myContext.getUserData(VARS[myFixType]);
    if (vars == null) {
      vars = ((UserDataHolderEx)myContext).putUserDataIfAbsent(VARS[myFixType], ContainerUtil.createConcurrentWeakMap());
    }
    final Map<PsiVariable, Boolean> finalVars = vars;
    return new AbstractCollection<>() {
      @Override
      public boolean add(PsiVariable psiVariable) {
        return finalVars.put(psiVariable, Boolean.TRUE) == null;
      }

      @NotNull
      @Override
      public Iterator<PsiVariable> iterator() {
        return finalVars.keySet().iterator();
      }

      @Override
      public int size() {
        return finalVars.size();
      }
    };
  }

  private void makeFinal() {
    for (PsiVariable var : getVariablesToFix()) {
      if (var.isValid()) {
        PsiUtil.setModifierProperty(var, PsiModifier.FINAL, true);
      }
    }
  }

  private static void makeArray(PsiVariable variable, PsiElement context) throws IncorrectOperationException {
    variable.normalizeDeclaration();
    PsiType type = variable.getType();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    PsiType newType = type.createArrayType();

    PsiDeclarationStatement variableDeclarationStatement;
    PsiExpression initializer = variable.getInitializer();
    PsiExpression init;
    if (initializer == null) {
      StringBuilder expression = new StringBuilder("[1]");
      while (type instanceof PsiArrayType) {
        expression.append("[1]");
        type = ((PsiArrayType) type).getComponentType();
      }
      init = factory.createExpressionFromText("new " + type.getCanonicalText() + expression, variable);
    }
    else {
      String explicitArrayDeclaration = JavaGenericsUtil.isReifiableType(type) ? "" : "new " + TypeConversionUtil.erasure(type).getCanonicalText() + "[]";
      init = factory.createExpressionFromText(explicitArrayDeclaration + "{ " + initializer.getText() + " }", variable);
    }
    variableDeclarationStatement = factory.createVariableDeclarationStatement(variable.getName(), newType, init);
    PsiVariable newVariable = (PsiVariable)variableDeclarationStatement.getDeclaredElements()[0];
    PsiUtil.setModifierProperty(newVariable, PsiModifier.FINAL, true);
    PsiElement newExpression = factory.createExpressionFromText(variable.getName() + "[0]", variable);

    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return;
    List<PsiReferenceExpression> outerReferences = new ArrayList<>();
    collectReferences(outerCodeBlock, variable, outerReferences);
    replaceReferences(outerReferences, newExpression);
    variable.replace(newVariable);
  }

  private static void copyToFinal(PsiVariable variable, PsiElement context) throws IncorrectOperationException {
    PsiManager psiManager = context.getManager();
    final Project project = psiManager.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiExpression initializer = factory.createExpressionFromText(variable.getName(), context);
    String newName = suggestNewName(project, variable);
    PsiType type = variable.getType();
    PsiDeclarationStatement copyDecl = factory.createVariableDeclarationStatement(newName, type, initializer);
    PsiVariable newVariable = (PsiVariable)copyDecl.getDeclaredElements()[0];
    final boolean mustBeFinal =
      !PsiUtil.isLanguageLevel8OrHigher(context) || JavaCodeStyleSettings.getInstance(context.getContainingFile()).GENERATE_FINAL_LOCALS;
    PsiUtil.setModifierProperty(newVariable, PsiModifier.FINAL, mustBeFinal);
    PsiElement statement = getStatementToInsertBefore(variable, context);
    if (statement == null) return;
    PsiExpression newExpression = factory.createExpressionFromText(newName, variable);
    replaceReferences(context, variable, newExpression);
    if (CommonJavaRefactoringUtil.isLoopOrIf(statement.getParent())) {
      CommonJavaRefactoringUtil.putStatementInLoopBody(copyDecl, statement.getParent(), statement);
    } else {
      statement.getParent().addBefore(copyDecl, statement);
    }
  }

  private static PsiElement getStatementToInsertBefore(PsiVariable variable, PsiElement context) {
    PsiElement declarationScope = variable instanceof PsiParameter
                                  ? ((PsiParameter)variable).getDeclarationScope() : PsiUtil.getVariableCodeBlock(variable, null);
    if (declarationScope == null) return null;

    PsiElement statement = context;
    nextInnerClass:
    do {
      statement = CommonJavaRefactoringUtil.getParentStatement(statement, false);

      if (statement == null || statement.getParent() == null) {
        return null;
      }
      PsiElement element = statement;
      while (element != declarationScope && !(element instanceof PsiFile)) {
        if (element instanceof PsiClass || element instanceof PsiLambdaExpression || element instanceof PsiSwitchLabelStatementBase) {
          statement = statement.getParent();
          continue nextInnerClass;
        }
        element = element.getParent();
      }
      return statement;
    }
    while (true);
  }

  private static String suggestNewName(Project project, PsiVariable variable) {
    // new name should not conflict with another variable at the variable declaration level and usage level
    String name = variable.getName();
    // trim last digit to suggest variable names like i1,i2, i3...
    if (name.length() > 1 && Character.isDigit(name.charAt(name.length()-1))) {
      name = name.substring(0,name.length()-1);
    }
    name = "final" + StringUtil.capitalize(StringUtil.trimStart(name, "final"));
    return JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(name, variable, true);
  }


  private static void replaceReferences(PsiElement context, final PsiVariable variable, final PsiElement newExpression) {
    context.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (expression.resolve() == variable)
          try {
            expression.replace(newExpression);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static void replaceReferences(List<? extends PsiReferenceExpression> references, PsiElement newExpression) throws IncorrectOperationException {
    for (PsiReferenceExpression reference : references) {
      reference.replace(newExpression);
    }
  }

  private static void collectReferences(PsiElement context, final PsiVariable variable, final List<? super PsiReferenceExpression> references) {
    context.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  @FixType
  private static int getQuickFixType(@NotNull PsiVariable variable) {
    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return UNKNOWN;
    List<PsiReferenceExpression> outerReferences = new ArrayList<>();
    collectReferences(outerCodeBlock, variable, outerReferences);

    @FixType
    int type = MAKE_FINAL;
    for (PsiReferenceExpression expression : outerReferences) {
      // if it happens that variable referenced from another inner class, make sure it can be make final from there
      PsiElement innerScope = HighlightControlFlowUtil.getElementVariableReferencedFrom(variable, expression);

      if (innerScope != null) {
        @FixType int thisType = MAKE_FINAL;
        if (writtenInside(variable, innerScope)) {
          // cannot make parameter array
          if (variable instanceof PsiParameter) return UNKNOWN;
          thisType = MAKE_ARRAY;
        }
        if (thisType == MAKE_FINAL && !canBeFinal(variable, outerReferences)) {
          thisType = COPY_TO_FINAL;
        }
        type = type >= thisType ? type : thisType;
      }
    }
    return type;
  }

  private static boolean canBeFinal(@NotNull PsiVariable variable, @NotNull List<? extends PsiReferenceExpression> references) {
    // if there is at least one assignment to this variable, it cannot be final
    Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems = new HashMap<>();
    Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems = new HashMap<>();
    for (PsiReferenceExpression expression : references) {
      if (ControlFlowUtil.isVariableAssignedInLoop(expression, variable)) return false;
      HighlightInfo.Builder highlightInfo = HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, variable, uninitializedVarProblems,
                                                                                                 variable.getContainingFile());
      if (highlightInfo != null) return false;
      highlightInfo = HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, finalVarProblems);
      if (highlightInfo != null) return false;
      if (variable instanceof PsiParameter && PsiUtil.isAccessedForWriting(expression)) return false;
    }
    return true;
  }

  private static boolean writtenInside(@NotNull PsiVariable variable, @NotNull PsiElement element) {
    if (element instanceof PsiAssignmentExpression assignmentExpression) {
      PsiExpression lExpression = assignmentExpression.getLExpression();
      if (lExpression instanceof PsiReferenceExpression ref && ref.resolve() == variable)
        return true;
    }
    else if (PsiUtil.isIncrementDecrementOperation(element)) {
      PsiElement operand = ((PsiUnaryExpression) element).getOperand();
      if (operand instanceof PsiReferenceExpression ref && ref.resolve() == variable)
        return true;
    }
    PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      if (writtenInside(variable, child)) return true;
    }
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void fixAccess(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    @FixType
    int type = getQuickFixType(variable);
    if (type == UNKNOWN) return;
    switch (type) {
      case MAKE_FINAL -> PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
      case MAKE_ARRAY -> makeArray(variable, context);
      case COPY_TO_FINAL -> copyToFinal(variable, context);
    }
  }
}
