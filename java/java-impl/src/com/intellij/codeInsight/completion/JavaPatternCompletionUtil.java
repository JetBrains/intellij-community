// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class JavaPatternCompletionUtil {
  /**
   * @param psiFile file (=element.getContainingFile())
   * @param element element where completion is invoked
   * @return true if patterns should be suggested here
   */
  public static boolean isPatternContext(@NotNull PsiFile psiFile, @NotNull PsiElement element) {
    if (!HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isAvailable(psiFile)) return false;
    if (!(element instanceof PsiIdentifier)) return false;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement parent1 = parent.getParent();
    if (parent1 instanceof PsiCaseLabelElementList) return true;
    if (!(parent1 instanceof PsiTypeElement)) return false;
    PsiElement parent2 = parent1.getParent();
    return parent2 instanceof PsiInstanceOfExpression;
  }

  /**
   * Suggests pattern completion variants based on a given class
   *
   * @param lookupElements consumer to sink suggestions into
   * @param context element where completion is invoked
   * @param psiClass class for which patterns should be suggested
   * @implNote currently, it suggests at most one record deconstruction pattern
   */
  public static void addPatterns(@NotNull Consumer<? super LookupElement> lookupElements, @NotNull PsiElement context, @NotNull PsiClass psiClass) {
    if (!psiClass.isRecord() || psiClass.getRecordComponents().length == 0 || psiClass.getName() == null) return;
    // TODO: support deconstruction with type parameters
    if (psiClass.getTypeParameters().length > 0) return;
    lookupElements.accept(PrioritizedLookupElement.withPriority(new RecordDeconstructionItem(PatternModel.create(psiClass, context)), -1.0));
  }

  private record PatternModel(@NotNull PsiClass record, @NotNull List<String> names) {
    static PatternModel create(@NotNull PsiClass record, @NotNull PsiElement context) {
      JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(record.getProject());
      List<String> names =
        ContainerUtil.map(record.getRecordComponents(), cmp -> manager.suggestUniqueVariableName(cmp.getName(), context, true));
      return new PatternModel(record, names);
    }

    String getCanonicalText() {
      return EntryStream.zip(Arrays.asList(record.getRecordComponents()), names)
        .mapKeyValue((cmp, name) -> cmp.getType().getCanonicalText() + " " + name)
        .joining(", ", record.getQualifiedName() + "(", ")");
    }

    @Override
    public String toString() {
      return EntryStream.zip(Arrays.asList(record.getRecordComponents()), names)
        .mapKeyValue((cmp, name) -> cmp.getType().getPresentableText() + " " + name)
        .joining(", ", record.getName() + "(", ")");
    }
  }

  private static class RecordDeconstructionItem extends LookupItem<PatternModel> {
    private final @NotNull PatternModel myModel;

    private RecordDeconstructionItem(@NotNull PatternModel model) {
      super(model, model.toString());
      myModel = model;
    }

    @Override
    public void renderElement(@NotNull LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setIcon(myModel.record().getIcon(0));
      presentation.setTailText(" " + PsiFormatUtil.getPackageDisplayName(myModel.record()), true);
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      int startOffset = context.getStartOffset();
      int endOffset = context.getEditor().getCaretModel().getOffset();
      Document document = context.getDocument();
      String canonicalText = myModel.getCanonicalText();
      document.replaceString(startOffset, endOffset, canonicalText);
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
      PsiDeconstructionPattern pattern = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(startOffset), PsiDeconstructionPattern.class);
      TextRange patternRange = pattern == null ? null : pattern.getTextRange();
      if (patternRange == null || patternRange.getStartOffset() != startOffset || patternRange.getLength() != canonicalText.length()) {
        document.replaceString(startOffset, startOffset+canonicalText.length(), myModel.toString());
        return;
      }
      pattern = (PsiDeconstructionPattern)JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(pattern);
      context.setTailOffset(pattern.getTextRange().getEndOffset());
      super.handleInsert(context);
    }
  }
}
