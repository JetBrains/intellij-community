// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordMember;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public final class HighlightRecordComponentsRecordFactory extends HighlightUsagesHandlerFactoryBase {
  @Nullable
  @Override
  public HighlightUsagesHandlerBase<PsiRecordComponent> createHighlightUsagesHandler(@NotNull Editor editor,
                                                                                     @NotNull PsiFile file,
                                                                                     @NotNull PsiElement target) {
    if (!(target instanceof PsiIdentifier)) return null;
    PsiElement parent = target.getParent();
    if (!(parent instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression)parent).resolve();
    if (!(resolved instanceof LightRecordMember member)) return null;

    PsiRecordComponent component = member.getRecordComponent();
    return new RecordComponentHighlightUsagesHandler(editor, file, component);
  }

  private static class RecordComponentHighlightUsagesHandler extends HighlightUsagesHandlerBase<PsiRecordComponent> {
    private final PsiRecordComponent myComponent;

    RecordComponentHighlightUsagesHandler(Editor editor, PsiFile file, PsiRecordComponent component) {
      super(editor, file);
      myComponent = component;
    }

    @Override
    public @NotNull List<PsiRecordComponent> getTargets() {
      return Collections.singletonList(myComponent);
    }

    @Override
    protected void selectTargets(@NotNull List<? extends PsiRecordComponent> targets, @NotNull Consumer<? super List<? extends PsiRecordComponent>> selectionConsumer) {
      selectionConsumer.consume(targets);
    }

    @Override
    public void computeUsages(@NotNull List<? extends PsiRecordComponent> targets) {
      assert targets.size() == 1;
      PsiRecordComponent record = targets.get(0);
      PsiIdentifier nameIdentifier = record.getNameIdentifier();
      if (nameIdentifier != null) {
        addOccurrence(nameIdentifier);
        final String name = nameIdentifier.getText();
        Consumer<PsiExpression> onOccurence = (expr) -> addOccurrence(expr);
        JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (isReferenceToRecordComponent(name, expression)) {
              onOccurence.consume(expression);
            }
          }
        };
        myComponent.getContainingFile().accept(visitor);
      }
    }

    private boolean isReferenceToRecordComponent(String name, PsiReferenceExpression referenceExpression) {
      if (!name.equals(referenceExpression.getReferenceName())) return false;
      LightRecordMember recordMember = tryCast(referenceExpression.resolve(), LightRecordMember.class);
      if (recordMember == null) return false;
      return recordMember.getRecordComponent() == myComponent;
    }
  }
}
