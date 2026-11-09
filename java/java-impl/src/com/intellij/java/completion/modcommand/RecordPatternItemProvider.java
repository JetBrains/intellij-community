// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaPatternCompletionUtil;
import com.intellij.codeInsight.completion.LimitedAccessibleClassPreprocessor;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDeconstructionList;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.JavaVarTypeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Completes record deconstruction patterns: {@code o instanceof Poi|} or {@code case Poi|} offers {@code Point(int x, int y)},
 * and the empty list of {@code Point(|} offers {@code int x, int y}.
 */
@NotNullByDefault
final class RecordPatternItemProvider extends JavaModCompletionItemProvider implements DumbAware {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    PsiElement position = context.getPosition();
    if (JavaPatternCompletionUtil.insideDeconstructionList(position)) {
      PsiClass record = getRecordOfEmptyDeconstructionList(position);
      if (record != null && record.getName() != null && record.getRecordComponents().length > 0) {
        sink.accept(createItem(PatternModel.create(record, position, true)));
      }
      return;
    }
    if (context.matcher().getPrefix().isEmpty() || !JavaPatternCompletionUtil.isPatternContext(context.getOriginalFile(), position)) {
      return;
    }
    if (((PsiJavaCodeReferenceElement)position.getParent()).isQualified()) return;
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(position);
    if (filter == null) return;
    boolean filterByScope = context.invocationCount() <= 2;
    boolean pkgContext = JavaCompletionUtil.inSomePackage(position);
    GlobalSearchScope scope = JavaClassNameCompletionContributor.getReferenceScope(context, filterByScope, false);
    AllClassesGetter.processJavaClasses(context.matcher(), position.getProject(), scope,
                                        new LimitedAccessibleClassPreprocessor(context, filterByScope, psiClass -> {
                                          if (psiClass.isRecord() && psiClass.getRecordComponents().length > 0 &&
                                              psiClass.getName() != null && psiClass.getTypeParameters().length == 0 &&
                                              filter.isAcceptable(psiClass, position) &&
                                              AllClassesGetter.isAcceptableInContext(position, psiClass, filterByScope, pkgContext)) {
                                            sink.accept(createItem(PatternModel.create(psiClass, position, false)));
                                          }
                                          return true;
                                        }));
  }

  private static @Nullable PsiClass getRecordOfEmptyDeconstructionList(PsiElement position) {
    PsiDeconstructionList deconstructionList = PsiTreeUtil.getParentOfType(position, PsiDeconstructionList.class);
    if (deconstructionList == null || deconstructionList.getDeconstructionComponents().length != 0) return null;
    PsiDeconstructionPattern deconstructionPattern = ObjectUtils.tryCast(deconstructionList.getParent(), PsiDeconstructionPattern.class);
    if (deconstructionPattern == null) return null;
    PsiClass psiRecord = PsiUtil.resolveClassInClassTypeOnly(deconstructionPattern.getTypeElement().getType());
    return psiRecord != null && psiRecord.isRecord() ? psiRecord : null;
  }

  private static CommonCompletionItem createItem(PatternModel model) {
    @NlsSafe String text = model.toString();
    MarkupText mainText = model.onlyDeconstructionList()
                          ? MarkupText.plainText(text)
                          : MarkupText.builder()
                            .append(text, MarkupText.Kind.NORMAL)
                            .append(" " + PsiFormatUtil.getPackageDisplayName(model.record()), MarkupText.Kind.GRAYED).build();
    return new CommonCompletionItem(text)
      .withPresentation(new ModCompletionItemPresentation(mainText).withMainIcon(() -> model.record().getIcon(0)))
      .withPriority(model.onlyDeconstructionList() ? 0.5 : -1.0)
      .withAdditionalUpdater((start, updater) -> insertPattern(model, start, updater));
  }

  /** Replaces the inserted text with the canonical one and shortens its class references; falls back to the presentable text. */
  private static void insertPattern(PatternModel model, int startOffset, ModPsiUpdater updater) {
    Document document = updater.getDocument();
    String canonicalText = model.getCanonicalText();
    document.replaceString(startOffset, updater.getCaretOffset(), canonicalText);
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
    PsiElement elementAtStart = updater.getPsiFile().findElementAt(startOffset);
    PsiElement deconstructionElement = model.onlyDeconstructionList()
                                       ? PsiTreeUtil.getParentOfType(elementAtStart, PsiDeconstructionList.class)
                                       : PsiTreeUtil.getParentOfType(elementAtStart, PsiDeconstructionPattern.class);
    TextRange deconstructionRange = deconstructionElement == null ? null : deconstructionElement.getTextRange();
    if (deconstructionRange == null ||
        deconstructionRange.getStartOffset() != startOffset ||
        deconstructionRange.getLength() != canonicalText.length()) {
      document.replaceString(startOffset, startOffset + canonicalText.length(), model.toString());
      updater.moveCaretTo(startOffset + model.toString().length());
      return;
    }
    deconstructionElement = JavaCodeStyleManager.getInstance(updater.getProject()).shortenClassReferences(deconstructionElement);
    updater.moveCaretTo(deconstructionElement.getTextRange().getEndOffset());
  }

  private record PatternModel(PsiClass record, List<String> names, List<PsiType> types, boolean onlyDeconstructionList) {
    static PatternModel create(PsiClass record, PsiElement context, boolean onlyDeconstructionList) {
      PsiDeconstructionPattern deconstructionPattern = PsiTreeUtil.getParentOfType(context, PsiDeconstructionPattern.class);
      List<String> names = new ArrayList<>();
      for (PsiRecordComponent component : record.getRecordComponents()) {
        String name = new VariableNameGenerator(context, VariableKind.LOCAL_VARIABLE)
          .byName(component.getName())
          .skipNames(names)
          .generate(true);
        names.add(name);
      }
      List<PsiType> types = findTypes(deconstructionPattern, record);
      return new PatternModel(record, names, types, onlyDeconstructionList);
    }

    private static @Unmodifiable List<PsiType> findTypes(@Nullable PsiDeconstructionPattern pattern, PsiClass record) {
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

  @Override
  public boolean isEnabled() {
    return true;
  }
}
