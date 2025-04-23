// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JavaSmartEnterProcessor extends AbstractBasicJavaSmartEnterProcessor {

  private static final List<Fixer> ourFixers =
    List.of(
      new LiteralFixer(),
      new MethodCallFixer(),
      new IfConditionFixer(),
      new ForStatementFixer(),
      new TernaryColonFixer(),
      new WhileConditionFixer(),
      new CatchDeclarationFixer(),
      new SwitchExpressionFixer(),
      new SwitchLabelColonFixer(),
      new DoWhileConditionFixer(),
      new BlockBraceFixer(),
      new MissingIfBranchesFixer(),
      new MissingTryBodyFixer(),
      new MissingSwitchBodyFixer(),
      new MissingLambdaBodyFixer(),
      new MissingCatchBodyFixer(),
      new MissingSynchronizedBodyFixer(),
      new MissingLoopBodyFixer(),
      new ParameterListFixer(),
      new MissingCommaFixer(),
      new MissingMethodBodyFixer(),
      new MissingClassBodyFixer(),
      new MissingReturnExpressionFixer(),
      new MissingThrowExpressionFixer(),
      new ParenthesizedFixer(),
      new SemicolonFixer(),
      new MissingArrayInitializerBraceFixer(),
      new MissingArrayConstructorBracketFixer(),
      new EnumFieldFixer());
  private static final EnterProcessor[] ourEnterProcessors = {
    new CommentBreakerEnterProcessor(),
    new AfterSemicolonEnterProcessor(),
    new LeaveCodeBlockEnterProcessor(),
    new PlainEnterProcessor()
  };
  private static final EnterProcessor[] ourAfterCompletionEnterProcessors = {
    new AfterSemicolonEnterProcessor(),
    new ASTNodeEnterProcessor() {
      @Override
      public boolean doEnter(@NotNull Editor editor, @NotNull ASTNode astNode, boolean isModified) {
        return PlainEnterProcessor.expandCodeBlock(editor, astNode);
      }
    }
  };

  public JavaSmartEnterProcessor() {
    super(ourFixers, ourEnterProcessors, ourAfterCompletionEnterProcessors,
          new JavadocFixer(),
          new CommentBreakerEnterProcessor()
    );
  }

  @Override
  protected void log(@NotNull Fixer fixer, @NotNull Project project) {
    FixerUsageCollector.log(project, fixer);
  }

  @Override
  protected boolean isImportStatementBase(PsiElement el) {
    return el instanceof PsiImportStatementBase;
  }

  protected static void plainEnter(final @NotNull Editor editor) {
    AbstractBasicJavaSmartEnterProcessor.plainEnter(editor);
  }

  protected static EditorActionHandler getEnterHandler() {
    return AbstractBasicJavaSmartEnterProcessor.getEnterHandler();
  }

  protected static boolean isModified(final @NotNull Editor editor) {
    return AbstractBasicJavaSmartEnterProcessor.isModified(editor);
  }

  // looks like it might be called on both FE and BE sides. be careful with 2x numbers.
  private static final class FixerUsageCollector extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("java.smart.enter.fixer", 3);
    private static final EventId1<String> USED = GROUP.registerEvent("fixer_used", new StringEventField.ValidatedByAllowedValues(
      "fixer_used",
      ContainerUtil.map(ourFixers, f -> f.getClass().getSimpleName())));

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }

    static void log(Project project, Fixer fixer) {
      USED.log(project, fixer.getClass().getSimpleName());
    }
  }
}
