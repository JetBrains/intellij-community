// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.JavaVarTypeUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class JavaPatternCompletionUtil {


  public static boolean insideDeconstructionList(@NotNull PsiElement element) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, element)) return false;
    return element.getParent() instanceof PsiJavaCodeReferenceElement ref &&
           ref.getParent() instanceof PsiTypeElement typeElement &&
           typeElement.getParent() instanceof PsiDeconstructionList;
  }

  /**
   * @param psiFile file (=element.getContainingFile())
   * @param element element where completion is invoked
   * @return true if patterns should be suggested here
   */
  public static boolean isPatternContext(@NotNull PsiFile psiFile, @NotNull PsiElement element) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, psiFile)) return false;
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
   * @param context        element where completion is invoked
   * @param psiClass       class for which patterns should be suggested
   * @implNote currently, it suggests at most one record deconstruction pattern
   */
  public static void addPatterns(@NotNull Consumer<? super LookupElement> lookupElements,
                                 @NotNull PsiElement context,
                                 @NotNull PsiClass psiClass,
                                 boolean onlyDeconstructionList) {
    if (!psiClass.isRecord() || psiClass.getRecordComponents().length == 0 || psiClass.getName() == null) return;
    if (psiClass.getTypeParameters().length > 0 && !onlyDeconstructionList) return;
    lookupElements.accept(
      PrioritizedLookupElement.withPriority(new RecordDeconstructionItem(PatternModel.create(psiClass, context, onlyDeconstructionList)),
                                            onlyDeconstructionList ? 0.5 : -1.0));
  }

  static void suggestPrimitivesInsideDeconstructionListPattern(@NotNull PsiElement currentPosition,
                                                               @NotNull Consumer<? super LookupElement> result) {
    PsiDeconstructionList deconstructionList = PsiTreeUtil.getParentOfType(currentPosition, PsiDeconstructionList.class);
    if (deconstructionList == null) return;
    PsiDeconstructionPattern deconstructionPattern = ObjectUtils.tryCast(deconstructionList.getParent(), PsiDeconstructionPattern.class);
    if (deconstructionPattern == null) return;
    PsiClass psiRecord = PsiUtil.resolveClassInClassTypeOnly(deconstructionPattern.getTypeElement().getType());
    if (psiRecord == null || !psiRecord.isRecord()) return;
    @NotNull PsiPattern @NotNull [] components = deconstructionList.getDeconstructionComponents();
    int indexOfPattern = -1;
    for (int i = 0; i < components.length; i++) {
      PsiPattern component = components[i];
      if (PsiTreeUtil.isAncestor(component, currentPosition, false)) {
        indexOfPattern = i;
        break;
      }
    }
    if (indexOfPattern == -1) {
      indexOfPattern = components.length;
    }
    PsiRecordComponent[] recordComponents = psiRecord.getRecordComponents();
    if (recordComponents.length < indexOfPattern) return;
    PsiRecordComponent component = recordComponents[indexOfPattern];
    PsiType type = component.getType();
    if (type instanceof PsiPrimitiveType) {
      LookupElement lookupItem = BasicExpressionCompletionContributor.createKeywordLookupItem(currentPosition, type.getCanonicalText());
      result.accept(new JavaKeywordCompletion.OverridableSpace(lookupItem, TailTypes.insertSpaceType()));
    }
  }

  static void suggestFullDeconstructionList(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiElement currentPosition = parameters.getPosition();
    PsiDeconstructionList deconstructionList = PsiTreeUtil.getParentOfType(currentPosition, PsiDeconstructionList.class);
    if (deconstructionList == null) return;
    PsiDeconstructionPattern deconstructionPattern = ObjectUtils.tryCast(deconstructionList.getParent(), PsiDeconstructionPattern.class);
    if (deconstructionPattern == null) return;
    PsiClass psiRecord = PsiUtil.resolveClassInClassTypeOnly(deconstructionPattern.getTypeElement().getType());
    if (psiRecord == null || !psiRecord.isRecord()) return;
    @NotNull PsiPattern @NotNull [] components = deconstructionList.getDeconstructionComponents();
    if (components.length != 0) return;
    addPatterns(result::addElement, currentPosition, psiRecord, true);
  }

  private record PatternModel(@NotNull PsiClass record,
                              @NotNull List<String> names,
                              @NotNull List<PsiType> types,
                              boolean onlyDeconstructionList) {
    static PatternModel create(@NotNull PsiClass record, @NotNull PsiElement context, boolean onlyDeconstructionList) {
      JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(record.getProject());
      PsiDeconstructionPattern deconstructionPattern = PsiTreeUtil.getParentOfType(context, PsiDeconstructionPattern.class);
      List<String> names =
        ContainerUtil.map(record.getRecordComponents(), cmp -> manager.suggestUniqueVariableName(cmp.getName(), context, true));
      List<PsiType> types = findTypes(deconstructionPattern, record);
      return new PatternModel(record, names, types, onlyDeconstructionList);
    }

    @NotNull
    private static List<PsiType> findTypes(@Nullable PsiDeconstructionPattern pattern, @NotNull PsiClass record) {
      List<PsiType> recordComponentTypes = Arrays.stream(record.getRecordComponents()).map(t -> t.getType()).toList();
      if (pattern == null) {
        return recordComponentTypes;
      }
      PsiType patternType = pattern.getTypeElement().getType();
      if (patternType instanceof PsiClassType) {
        patternType = PsiUtil.captureToplevelWildcards(patternType, pattern);
        PsiSubstitutor substitutor = ((PsiClassType)patternType).resolveGenerics().getSubstitutor();
        return ContainerUtil.map(recordComponentTypes,
                                 recordComponentType -> JavaVarTypeUtil.getUpwardProjection(substitutor.substitute(recordComponentType)));
      }

      return recordComponentTypes;
    }

    String getCanonicalText() {
      return EntryStream.zip(types, names)
        .mapKeyValue((type, name) -> type.getCanonicalText() + " " + name)
        .joining(", ", onlyDeconstructionList ? "" : record.getQualifiedName() + "(", onlyDeconstructionList ? "" : ")");
    }

    @Override
    public String toString() {
      return EntryStream.zip(types, names)
        .mapKeyValue((type, name) -> type.getPresentableText() + " " + name)
        .joining(", ", onlyDeconstructionList ? "" : record.getName() + "(", onlyDeconstructionList ? "" : ")");
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
      if (!myModel.onlyDeconstructionList) {
        presentation.setTailText(" " + PsiFormatUtil.getPackageDisplayName(myModel.record()), true);
      }
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      int startOffset = context.getStartOffset();
      int endOffset = context.getEditor().getCaretModel().getOffset();
      Document document = context.getDocument();
      String canonicalText = myModel.getCanonicalText();
      document.replaceString(startOffset, endOffset, canonicalText);
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
      PsiElement pattern;
      if (!myModel.onlyDeconstructionList) {
        pattern = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(startOffset), PsiDeconstructionPattern.class);
      }
      else {
        pattern = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(startOffset), PsiDeconstructionList.class);
      }
      TextRange patternRange = pattern == null ? null : pattern.getTextRange();
      if (patternRange == null || patternRange.getStartOffset() != startOffset || patternRange.getLength() != canonicalText.length()) {
        document.replaceString(startOffset, startOffset + canonicalText.length(), myModel.toString());
        return;
      }
      pattern = JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(pattern);
      context.setTailOffset(pattern.getTextRange().getEndOffset());
      super.handleInsert(context);
    }
  }
}
