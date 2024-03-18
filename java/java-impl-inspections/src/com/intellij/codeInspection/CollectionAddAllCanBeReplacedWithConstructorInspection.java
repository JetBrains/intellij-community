// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.performance.CollectionsListSettings;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class CollectionAddAllCanBeReplacedWithConstructorInspection extends AbstractBaseJavaLocalInspectionTool {
  private final CollectionsListSettings mySettings = new CollectionsListSettings() {
    @Override
    protected Collection<String> getDefaultSettings() {
      return Collections.emptyList();
    }
  };

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    mySettings.writeSettings(node);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return mySettings.getOptionPane();
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return mySettings.getOptionController();
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    mySettings.readSettings(node);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiElement nameElement = methodExpression.getReferenceNameElement();
        final String methodName = methodExpression.getReferenceName();
        if (nameElement == null || !"addAll".equals(methodName) && !"putAll".equals(methodName)) return;
        PsiExpression[] args = expression.getArgumentList().getExpressions();
        if (args.length != 1) return;
        final PsiExpressionStatement parent = ObjectUtils.tryCast(expression.getParent(), PsiExpressionStatement.class);
        if (parent == null) return;
        PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(methodExpression.getQualifierExpression());
        if (variable == null) return;
        if (PsiUtil.resolveClassInClassTypeOnly(variable.getType()) == null) return;
        if (statementHasSubsequentAddAll(parent, variable, methodName)) return;
        PsiType argType = args[0].getType();
        if (!InheritanceUtil.isInheritor(argType, "putAll".equals(methodName) ?
                                                  CommonClassNames.JAVA_UTIL_MAP : CommonClassNames.JAVA_UTIL_COLLECTION)) {
          return;
        }

        InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(variable, parent);

        PsiNewExpression assignmentExpression;
        if (status == InitializerUsageStatus.DECLARED_JUST_BEFORE || status == InitializerUsageStatus.AT_WANTED_PLACE_ONLY) {
          if (!isCollectionConstructor(variable.getInitializer())) return;
          assignmentExpression = (PsiNewExpression)variable.getInitializer();
        }
        else {
          assignmentExpression = getPreviousAssignment(variable, parent);
        }

        if (assignmentExpression == null || !isAddAllReplaceable(expression, assignmentExpression)) return;
        if (mayInheritComparator(args, argType, assignmentExpression)) return;
        final PsiMethod method = expression.resolveMethod();
        if (method != null) {
          holder.registerProblem(nameElement, QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.description"),
                                 new ReplaceAddAllWithConstructorFix(assignmentExpression, expression, methodName));
        }
      }

      private boolean mayInheritComparator(PsiExpression[] args, PsiType argType, PsiNewExpression assignmentExpression) {
        PsiType type = assignmentExpression.getType();
        PsiClass collectionType = Objects.requireNonNull(PsiUtil.resolveClassInClassTypeOnly(type));
        String name = Objects.requireNonNull(collectionType.getQualifiedName());
        return switch (name) {
          case "java.util.TreeSet", "java.util.concurrent.ConcurrentSkipListSet" ->
            // If declared arg type inherits SortedSet, the (SortedSet) copy constructor will be invoked, which inherits the comparator
            InheritanceUtil.isInheritor(argType, CommonClassNames.JAVA_UTIL_SORTED_SET);
          case "java.util.TreeMap", "java.util.concurrent.ConcurrentSkipListMap" ->
            // If declared arg type inherits SortedMap, the (SortedMap) copy constructor will be invoked, which inherits the comparator
            InheritanceUtil.isInheritor(argType, CommonClassNames.JAVA_UTIL_SORTED_MAP);
          case "java.util.PriorityQueue", "java.util.concurrent.PriorityBlockingQueue" -> {
            // Here even (Collection) copy constructor inherits the comparator using runtime type checks, so we should be more conservative
            TypeConstraint constraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(args[0]));
            PsiClassType sortedSet = JavaPsiFacade.getElementFactory(holder.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_UTIL_SORTED_SET);
            yield constraint.meet(TypeConstraints.instanceOf(sortedSet)) != TypeConstraints.BOTTOM ||
                  constraint.meet(TypeConstraints.instanceOf(type)) != TypeConstraints.BOTTOM;
          }
          default -> false;
        };
      }
    };
  }

  private PsiNewExpression getPreviousAssignment(PsiLocalVariable variable, PsiStatement statement) {
    while (true) {
      statement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
      if (statement == null) return null;
      PsiExpression expression = ExpressionUtils.getAssignmentTo(statement, variable);
      if (isCollectionConstructor(expression)) {
        return (PsiNewExpression)expression;
      }
      if (VariableAccessUtils.variableIsUsed(variable, statement)) return null;
    }
  }

  private static boolean statementHasSubsequentAddAll(@NotNull PsiElement statement,
                                                      @NotNull PsiLocalVariable referent,
                                                      @NotNull String previousMethodName) {
    final PsiElement sibling = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (sibling instanceof PsiExpressionStatement expressionStatement) {
      final PsiExpression siblingExpression = expressionStatement.getExpression();
      if (siblingExpression instanceof PsiMethodCallExpression siblingMethodCall) {
        final PsiExpression qualifier = siblingMethodCall.getMethodExpression().getQualifierExpression();
        if (qualifier instanceof PsiReferenceExpression ref && referent.isEquivalentTo(ref.resolve())) {
          final PsiMethod method = siblingMethodCall.resolveMethod();
          if (method != null && method.getName().equals(previousMethodName)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean isCollectionConstructor(PsiExpression initializer) {
    if (!(initializer instanceof PsiNewExpression newExpression)) {
      return false;
    }
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) {
      return false;
    }
    final PsiClass initializerClass = (PsiClass)classReference.resolve();
    if (initializerClass == null) return false;
    if (!ConstructionUtils.isCollectionWithCopyConstructor(initializerClass) &&
        (!mySettings.getCollectionClassesRequiringCapacity().contains(initializerClass.getQualifiedName()) ||
         !hasProperConstructor(initializerClass))) {
      return false;
    }
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    return argumentList != null && argumentList.isEmpty();
  }

  private static boolean hasProperConstructor(PsiClass psiClass) {
    for (PsiMethod psiMethod : psiClass.getConstructors()) {
      PsiParameterList parameterList = psiMethod.getParameterList();
      if(parameterList.getParametersCount() == 1) {
        PsiParameter parameter = Objects.requireNonNull(parameterList.getParameter(0));
        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null) {
          PsiType type = typeElement.getType();
          if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
              InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isAddAllReplaceable(final PsiExpression addAllExpression, PsiNewExpression newExpression) {
    final boolean[] isReplaceable = new boolean[]{true};
    final PsiFile newExpressionContainingFile = newExpression.getContainingFile();
    final TextRange newExpressionTextRange = newExpression.getTextRange();

    addAllExpression.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        final PsiElement resolved = expression.resolve();
        if (PsiUtil.isJvmLocalVariable(resolved)) {
          PsiVariable variable = (PsiVariable) resolved;
          final LocalSearchScope useScope = (LocalSearchScope)variable.getUseScope();
          if (!useScope.containsRange(newExpressionContainingFile, newExpressionTextRange)) {
            isReplaceable[0] = false;
          }
        }
      }
    });

    return isReplaceable[0];
  }

  private static class ReplaceAddAllWithConstructorFix extends PsiUpdateModCommandQuickFix {
    private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCallExpression;
    private final SmartPsiElementPointer<PsiNewExpression> myNewExpression;
    private final String methodName;

    ReplaceAddAllWithConstructorFix(PsiNewExpression newExpression, PsiMethodCallExpression expression, String methodName) {
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(newExpression.getProject());
      myMethodCallExpression = smartPointerManager.createSmartPsiElementPointer(expression);
      myNewExpression = smartPointerManager.createSmartPsiElementPointer(newExpression);
      this.methodName = methodName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.name", methodName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiMethodCallExpression methodCallExpression = updater.getWritable(myMethodCallExpression.getElement());
      if (methodCallExpression == null) return;
      PsiExpressionStatement expressionStatement = ObjectUtils.tryCast(methodCallExpression.getParent(), PsiExpressionStatement.class);
      if (expressionStatement == null) return;
      final PsiNewExpression newExpression = updater.getWritable(myNewExpression.getElement());
      if (newExpression == null) return;
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(newExpression.getParent());
      PsiVariable variable = null;
      if (parent instanceof PsiVariable) {
        variable = (PsiVariable)parent;
      }
      else if (parent instanceof PsiAssignmentExpression) {
        variable = ExpressionUtils.resolveLocalVariable(((PsiAssignmentExpression)parent).getLExpression());
      }
      if (variable == null) return;
      PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
      if (reference == null) return;

      CommentTracker ct = new CommentTracker();
      final PsiElement parameter = methodCallExpression.getArgumentList().getExpressions()[0];
      String replacement = "new " + reference.getText() + "(" + ct.text(parameter) + ")";
      if (parent instanceof PsiAssignmentExpression) {
        ct.delete(parent);
      }
      else {
        if (variable.getParent() instanceof PsiDeclarationStatement &&
            ((PsiDeclarationStatement)variable.getParent()).getDeclaredElements().length == 1) {
          PsiElement scope = PsiTreeUtil.getParentOfType(expressionStatement, PsiMember.class, PsiStatement.class, PsiLambdaExpression.class);
          if (scope != null &&
              ReferencesSearch.search(variable).allMatch(ref -> PsiTreeUtil.isAncestor(scope, ref.getElement(), true))) {
            PsiDeclarationStatement newDeclaration =
              JavaPsiFacade.getElementFactory(project).createVariableDeclarationStatement("x", PsiTypes.intType(), null, methodCallExpression);
            PsiVariable newVariable = (PsiVariable)newDeclaration.getDeclaredElements()[0].replace(variable);
            ct.delete(variable);
            ct.replace(Objects.requireNonNull(newVariable.getInitializer()), replacement);
            ct.replaceAndRestoreComments(expressionStatement, newDeclaration);
            return;
          }
        }
        ct.delete(newExpression);
      }
      ct.replaceAndRestoreComments(methodCallExpression, variable.getName() + "=" + replacement);
    }
  }
}