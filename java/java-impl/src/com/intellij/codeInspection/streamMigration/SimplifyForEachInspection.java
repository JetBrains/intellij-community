/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public class SimplifyForEachInspection extends BaseJavaBatchLocalInspectionTool {
  private static final CallMatcher.Simple ITERABLE_FOREACH =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ITERABLE, "forEach").parameterCount(1);
  private static final CallMatcher.Simple STREAM_FOREACH_ORDERED =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "forEachOrdered").parameterCount(1);
  private static final CallMatcher STREAM_FOREACH =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "forEach", "forEachOrdered").parameterCount(1);


  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "forEach call can be simplified";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    VirtualFile virtualFile = file.getVirtualFile();
    if (!PsiUtil.isLanguageLevel8OrHigher(file) || virtualFile == null ||
        !FileIndexFacade.getInstance(holder.getProject()).isInSourceContent(virtualFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        SimplifyForEachContext context = SimplifyForEachContext.from(call);
        if (context == null) return;
        boolean opCountChanged = context.myTerminalBlock.getOperationCount() > 1;
        boolean lastOpChanged = !(context.myMigration instanceof ForEachMigration);
        if (opCountChanged || lastOpChanged) {
          String customMessage = lastOpChanged ? "Replace with " + context.myMigration.getReplacement() : "Extract intermediate operations";
          ProblemHighlightType highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          holder.registerProblem(context.myMainStatement, customMessage, highlightType, getRange(call).shiftRight(-call.getTextOffset()),
                                 new SimplifyForEachFix(customMessage));
        }
      }
    };
  }

  @NotNull
  private static TextRange getRange(PsiMethodCallExpression call) {
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    return new TextRange(methodExpression.getTextOffset(), call.getArgumentList().getTextOffset());
  }

  @Nullable
  static TerminalBlock extractTerminalBlock(@Nullable PsiElement lambdaBody,
                                            @NotNull ExistingStreamSource source) {
    if (lambdaBody instanceof PsiCodeBlock) {
      return TerminalBlock.from(source, (PsiCodeBlock)lambdaBody);
    }
    if (lambdaBody instanceof PsiExpression) {
      return TerminalBlock.fromStatements(source, new LightExpressionStatement((PsiExpression)lambdaBody));
    }
    return null;
  }

  @Nullable
  static PsiLambdaExpression extractLambdaFromForEach(PsiMethodCallExpression call) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null || !(STREAM_FOREACH.test(call) || isCollectionForEach(call, qualifier))) return null;
    PsiExpression arg = call.getArgumentList().getExpressions()[0];
    return tryCast(PsiUtil.skipParenthesizedExprDown(arg), PsiLambdaExpression.class);
  }

  private static boolean isCollectionForEach(PsiMethodCallExpression call, PsiExpression qualifier) {
    return ITERABLE_FOREACH.test(call) && InheritanceUtil.isInheritor(qualifier.getType(), CommonClassNames.JAVA_UTIL_COLLECTION);
  }

  static class LightExpressionStatement extends LightElement implements PsiExpressionStatement {
    @NotNull private final PsiExpression myExpression;

    protected LightExpressionStatement(@NotNull PsiExpression expression) {
      super(expression.getManager(), JavaLanguage.INSTANCE);
      myExpression = expression;
    }

    @NotNull
    @Override
    public PsiExpression getExpression() {
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
    String createReplacement() {
      return myExpression.getText() + (myIsCollectionForEach? ".stream()" : "");
    }

    @Nullable
    static ExistingStreamSource extractSource(PsiMethodCallExpression call, PsiLambdaExpression lambda) {
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

  static class SimplifyForEachContext {
    @NotNull private final TerminalBlock myTerminalBlock;
    @NotNull private final PsiStatement myMainStatement;
    @NotNull private final BaseStreamApiMigration myMigration;
    @NotNull private final PsiElement myLambdaBody;

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

    static SimplifyForEachContext from(PsiMethodCallExpression call) {
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

  public static class SimplifyForEachFix implements LocalQuickFix {
    @NotNull private final String myCustomName;

    protected SimplifyForEachFix(@NotNull String customName) {
      myCustomName = customName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myCustomName;
    }

    @SuppressWarnings("DialogTitleCapitalization")
    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify forEach lambda";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpressionStatement statement = tryCast(descriptor.getPsiElement(), PsiExpressionStatement.class);
      if (statement == null) return;
      PsiMethodCallExpression call = tryCast(statement.getExpression(), PsiMethodCallExpression.class);
      if (call == null) return;
      SimplifyForEachInspection.SimplifyForEachContext simplifyForEachContext = SimplifyForEachInspection.SimplifyForEachContext.from(call);
      if (simplifyForEachContext == null) return;
      PsiElement result = simplifyForEachContext.migrate();
      if (result == null) return;
      MigrateToStreamFix.simplifyAndFormat(project, result);
    }
  }

  public static class ForEachNonFinalFix extends PsiElementBaseIntentionAction implements HighPriorityAction {
    private final PsiElement myContext;

    public ForEachNonFinalFix(PsiElement context) {
      SimplifyForEachContext simplifyContext = findMigration(context);
      if (simplifyContext == null) {
        myContext = null;
      }
      else {
        myContext = context;
        setText("Avoid mutation using Stream API '" + simplifyContext.myMigration.getReplacement() + "' operation");
      }
    }

    private static SimplifyForEachContext findMigration(PsiElement context) {
      if (!(context instanceof PsiReferenceExpression) || !PsiUtil.isAccessedForWriting((PsiExpression)context)) return null;
      PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(context, PsiLambdaExpression.class);
      if (lambda == null) return null;
      PsiElement lambdaBody = lambda.getBody();
      if (lambdaBody == null) return null;
      PsiExpressionList parameters = tryCast(PsiUtil.skipParenthesizedExprUp(lambda.getParent()), PsiExpressionList.class);
      if (parameters == null || parameters.getExpressions().length != 1) return null;
      PsiMethodCallExpression call = tryCast(parameters.getParent(), PsiMethodCallExpression.class);
      SimplifyForEachContext simplifyForEachContext = SimplifyForEachContext.from(call);
      if (simplifyForEachContext == null || simplifyForEachContext.myMigration instanceof ForEachMigration) return null;
      return simplifyForEachContext;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
      PsiMethodCallExpression call =
        PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class), PsiMethodCallExpression.class);
      SimplifyForEachContext simplifyForEachContext = SimplifyForEachContext.from(call);
      if (simplifyForEachContext != null) {
        PsiElement result = simplifyForEachContext.migrate();
        if (result != null) {
          MigrateToStreamFix.simplifyAndFormat(project, result);
        }
      }
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
      return myContext != null && myContext.isValid();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Avoid mutation using Stream API";
    }
  }
}
