// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordMember;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class HighlightRecordComponentsRecordFactory extends HighlightUsagesHandlerFactoryBase {
  @Override
  public @Nullable HighlightUsagesHandlerBase<PsiRecordComponent> createHighlightUsagesHandler(@NotNull Editor editor,
                                                                                               @NotNull PsiFile file,
                                                                                               @NotNull PsiElement target) {
    if (!(target instanceof PsiIdentifier)) return null;
    PsiElement parent = target.getParent();
    if (!(parent instanceof PsiReferenceExpression ref)) return null;
    PsiElement resolved = ref.resolve();
    if (!(resolved instanceof LightRecordMember member)) return null;

    return new RecordComponentHighlightUsagesHandler(editor, file, member.getRecordComponent());
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
    protected void selectTargets(@NotNull List<? extends PsiRecordComponent> targets, 
                                 @NotNull Consumer<? super List<? extends PsiRecordComponent>> selectionConsumer) {
      selectionConsumer.consume(targets);
    }

    @Override
    public void computeUsages(@NotNull List<? extends PsiRecordComponent> targets) {
      assert targets.size() == 1;
      PsiIdentifier nameIdentifier = targets.get(0).getNameIdentifier();
      if (nameIdentifier == null) return;
      boolean componentHighlighted = nameIdentifier.getContainingFile() == myFile;
      if (componentHighlighted) addOccurrence(nameIdentifier);
      final String name = nameIdentifier.getText();
      JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          PsiElement element = expression.getReferenceNameElement();
          if (element != null &&
              name.equals(element.getText()) &&
              expression.resolve() instanceof LightRecordMember member &&
              member.getRecordComponent() == myComponent) {
            addOccurrence(element);
          }
        }
      };
      myFile.accept(visitor);
      buildStatusText(null, componentHighlighted ? myReadUsages.size() - 1 : myReadUsages.size());
    }

    @Override
    protected void addOccurrence(@NotNull PsiElement element) {
      super.addOccurrence(element);
    }
  }
}
