// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public final class SimplifyForEachInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher.Simple ITERABLE_FOREACH =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ITERABLE, "forEach").parameterCount(1);
  private static final CallMatcher.Simple STREAM_FOREACH_ORDERED =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "forEachOrdered").parameterCount(1);
  private static final CallMatcher STREAM_FOREACH =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "forEach", "forEachOrdered").parameterCount(1);


  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ADVANCED_COLLECTIONS_API);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        SimplifyForEachContext context = SimplifyForEachContext.from(call);
        if (context == null) return;
        boolean opCountChanged = context.myTerminalBlock.getOperationCount() > 1;
        boolean lastOpChanged = !(context.myMigration instanceof ForEachMigration);
        if (opCountChanged || lastOpChanged) {
            String customMessage = lastOpChanged ?
                                   CommonQuickFixBundle.message("fix.replace.with.x", context.myMigration.getReplacement() + "()") :
                                   JavaBundle.message("inspection.simplify.for.each.extract.intermediate.operations");
          ProblemHighlightType highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          holder.registerProblem(context.myMainStatement, customMessage, highlightType, getRange(call).shiftRight(-call.getTextOffset()),
                                 new SimplifyForEachFix(customMessage));
        }
      }
    };
  }

  private static @NotNull TextRange getRange(PsiMethodCallExpression call) {
    if(!InspectionProjectProfileManager.isInformationLevel("SimplifyForEach", call)) {
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      return new TextRange(methodExpression.getTextOffset(), call.getArgumentList().getTextOffset());
    }
    return call.getTextRange();
  }

  static @Nullable TerminalBlock extractTerminalBlock(@Nullable PsiElement lambdaBody,
                                                      @NotNull ExistingStreamSource source) {
    if (lambdaBody instanceof PsiCodeBlock) {
      return TerminalBlock.from(source, (PsiCodeBlock)lambdaBody);
    }
    if (lambdaBody instanceof PsiExpression) {
      return TerminalBlock.fromStatements(source, new LightExpressionStatement((PsiExpression)lambdaBody));
    }
    return null;
  }

  static @Nullable PsiLambdaExpression extractLambdaFromForEach(@NotNull PsiMethodCallExpression call) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null || !(STREAM_FOREACH.test(call) || isCollectionForEach(call, qualifier))) return null;
    PsiExpression arg = call.getArgumentList().getExpressions()[0];
    return tryCast(PsiUtil.skipParenthesizedExprDown(arg), PsiLambdaExpression.class);
  }

  private static boolean isCollectionForEach(PsiMethodCallExpression call, PsiExpression qualifier) {
    return ITERABLE_FOREACH.test(call) && InheritanceUtil.isInheritor(qualifier.getType(), CommonClassNames.JAVA_UTIL_COLLECTION);
  }

  static class LightExpressionStatement extends LightElement implements PsiExpressionStatement {
    private final @NotNull PsiExpression myExpression;

    protected LightExpressionStatement(@NotNull PsiExpression expression) {
      super(expression.getManager(), JavaLanguage.INSTANCE);
      myExpression = expression;
    }

    @Override
    public @NotNull PsiExpression getExpression() {
      return myExpression;
    }

    @Override
    public String toString() {
      return myExpression.getText() + ";";
    }
  }


  static class ExistingStreamSource extends StreamApiMigrationInspection.StreamSource {
    private final boolean myIsCollectionForEach;

    protected ExistingStreamSource(PsiStatement mainStatement, PsiVariable variable, PsiExpression expression, boolean isCollectionForEach) {
      super(mainStatement, variable, expression);
      myIsCollectionForEach = isCollectionForEach;
    }

    @Override
    String createReplacement(CommentTracker ct) {
      return myExpression.getText() + (myIsCollectionForEach? ".stream()" : "");
    }

    static @Nullable ExistingStreamSource extractSource(PsiMethodCallExpression call, PsiLambdaExpression lambda) {
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return null;
      PsiParameter parameter = parameters[0];

      boolean isCollectionForEach = ITERABLE_FOREACH.test(call);

      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return null;
      PsiStatement parent = tryCast(call.getParent(), PsiExpressionStatement.class);
      if (parent == null) return null;
      return new ExistingStreamSource(parent, parameter, qualifier, isCollectionForEach);
    }
  }

  static final class SimplifyForEachContext {
    private final @NotNull TerminalBlock myTerminalBlock;
    private final @NotNull PsiStatement myMainStatement;
    private final @NotNull BaseStreamApiMigration myMigration;
    private final @NotNull PsiElement myLambdaBody;

    private SimplifyForEachContext(@NotNull TerminalBlock terminalBlock,
                                   @NotNull PsiStatement mainStatement,
                                   @NotNull PsiElement body,
                                   @NotNull BaseStreamApiMigration migration) {
      myTerminalBlock = terminalBlock;
      myMainStatement = mainStatement;
      myLambdaBody = body;
      myMigration = migration;
    }

    public PsiElement migrate() {
      PsiElement result = myMigration.migrate(myMainStatement.getProject(), myLambdaBody, myTerminalBlock);
      if (result != null) {
        myTerminalBlock.operations().forEach(StreamApiMigrationInspection.Operation::cleanUp);
      }
      return result;
    }

    static SimplifyForEachContext from(@Nullable PsiMethodCallExpression call) {
      if (call == null) return null;
      PsiLambdaExpression lambda = extractLambdaFromForEach(call);
      if (lambda == null) return null;
      PsiElement lambdaBody = lambda.getBody();
      ExistingStreamSource
        source = ExistingStreamSource.extractSource(call, lambda);
      if (source == null) return null;
      TerminalBlock terminalBlock = extractTerminalBlock(lambdaBody, source);
      if (terminalBlock == null) return null;

      PsiStatement mainStatement = source.getMainStatement();
      BaseStreamApiMigration migration =
        StreamApiMigrationInspection.findMigration(mainStatement, lambdaBody, terminalBlock, true, true);
      if (migration instanceof ForEachMigration && STREAM_FOREACH_ORDERED.test(call)) {
        migration = new ForEachMigration(migration.isShouldWarn(), "forEachOrdered");
      }
      return migration == null ? null : new SimplifyForEachContext(terminalBlock, mainStatement, lambdaBody, migration);
    }
  }

  public static class SimplifyForEachFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull @Nls String myCustomName;

    protected SimplifyForEachFix(@NotNull @Nls String customName) {
      myCustomName = customName;
    }

    @Override
    public @Nls @NotNull String getName() {
      return myCustomName;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.simplify.foreach.lambda");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiExpressionStatement statement = tryCast(element, PsiExpressionStatement.class);
      if (statement == null) return;
      PsiMethodCallExpression call = tryCast(statement.getExpression(), PsiMethodCallExpression.class);
      if (call == null) return;
      SimplifyForEachInspection.SimplifyForEachContext simplifyForEachContext = SimplifyForEachInspection.SimplifyForEachContext.from(call);
      if (simplifyForEachContext == null) return;
      PsiElement result = simplifyForEachContext.migrate();
      if (result == null) return;
      MigrateToStreamFix.simplify(project, result);
    }
  }

  public static final class ForEachNonFinalFix extends PsiUpdateModCommandAction<PsiElement> {
    private final @Nullable PsiElement myContext;
    private final @Nls String myMessage;

    public ForEachNonFinalFix(@Nullable PsiElement context) {
      super(PsiElement.class);
      SimplifyForEachContext simplifyContext = findMigration(context);
      if (simplifyContext == null) {
        myContext = null;
        myMessage = null;
      }
      else {
        myContext = context;
        myMessage =
          JavaBundle.message("quickfix.text.avoid.mutation.using.stream.api.0.operation", simplifyContext.myMigration.getReplacement());
      }
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
      return myContext == null || !myContext.isValid() ? null :
             Presentation.of(myMessage).withPriority(PriorityAction.Priority.HIGH);
    }

    private static SimplifyForEachContext findMigration(@Nullable PsiElement context) {
      if (!(context instanceof PsiReferenceExpression) || !PsiUtil.isAccessedForWriting((PsiExpression)context)) return null;
      PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(context, PsiLambdaExpression.class);
      if (lambda == null) return null;
      PsiElement lambdaBody = lambda.getBody();
      if (lambdaBody == null) return null;
      PsiExpressionList parameters = tryCast(PsiUtil.skipParenthesizedExprUp(lambda.getParent()), PsiExpressionList.class);
      if (parameters == null || parameters.getExpressionCount() != 1) return null;
      PsiMethodCallExpression call = tryCast(parameters.getParent(), PsiMethodCallExpression.class);
      SimplifyForEachContext simplifyForEachContext = SimplifyForEachContext.from(call);
      if (simplifyForEachContext == null || simplifyForEachContext.myMigration instanceof ForEachMigration) return null;
      return simplifyForEachContext;
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call =
        PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class), PsiMethodCallExpression.class);
      SimplifyForEachContext simplifyForEachContext = SimplifyForEachContext.from(call);
      if (simplifyForEachContext != null) {
        PsiElement result = simplifyForEachContext.migrate();
        if (result != null) {
          MigrateToStreamFix.simplify(context.project(), result);
        }
      }
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.avoid.mutation.using.stream.api");
    }
  }
}
