// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.makefinal.EffectivelyFinalFixer;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ConvertToStreamFixer implements EffectivelyFinalFixer {
  @Override
  public boolean isAvailable(@NotNull PsiLocalVariable var) {
    return createModel(var) != null;
  }

  @Override
  public void fix(@NotNull PsiLocalVariable var) {
    StreamModel model = createModel(var);
    if (model == null) return;
    PsiElement element = model.migration().migrate(var.getProject(), model.body(), model.tb());
    MigrateToStreamFix.simplify(var.getProject(), element);
  }

  @Override
  public String getText(@NotNull PsiLocalVariable var) {
    return JavaBundle.message("intention.make.final.fixer.stream", var.getName());
  }

  private static StreamModel createModel(PsiLocalVariable var) {
    PsiElement block = PsiUtil.getVariableCodeBlock(var, null);
    if (block == null) return null;
    List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(var, block);
    List<PsiReferenceExpression> writes = ContainerUtil.filter(references, PsiUtil::isAccessedForWriting);
    if (writes.isEmpty()) return null;
    PsiElement commonParent = PsiTreeUtil.findCommonParent(writes);
    if (commonParent == null) return null;
    while (commonParent.getParent() != block) {
      commonParent = commonParent.getParent();
      if (commonParent == null) return null;
    }
    if (!(commonParent instanceof PsiLoopStatement statement)) return null;
    final PsiStatement body = statement.getBody();
    if (body == null) return null;
    StreamApiMigrationInspection.StreamSource source = StreamApiMigrationInspection.StreamSource.tryCreate(statement);
    if (source == null) return null;
    if (!ExceptionUtil.getThrownCheckedExceptions(body).isEmpty()) return null;
    TerminalBlock tb = TerminalBlock.from(source, body);
    BaseStreamApiMigration migration = StreamApiMigrationInspection.findMigration(statement, body, tb, false, false);
    if (migration == null) return null;
    ControlFlowUtils.InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, statement);
    if (status != ControlFlowUtils.InitializerUsageStatus.DECLARED_JUST_BEFORE &&
        status != ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE_ONLY) {
      return null;
    }
    return new StreamModel(migration, body, tb);
  }

  private record StreamModel(BaseStreamApiMigration migration, PsiStatement body, TerminalBlock tb) {
  }
}
