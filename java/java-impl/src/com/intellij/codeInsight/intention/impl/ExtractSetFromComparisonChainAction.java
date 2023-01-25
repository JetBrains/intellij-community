// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.EqualityCheck;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class ExtractSetFromComparisonChainAction extends PsiElementBaseIntentionAction {
  private static class Holder {
    private static final String GUAVA_IMMUTABLE_SET = "com.google.common.collect.ImmutableSet";
    private static final String INITIALIZER_FORMAT_GUAVA = GUAVA_IMMUTABLE_SET + ".of({0})";
    private static final String INITIALIZER_FORMAT_JAVA2 =
      CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSet(" +
      "new " + CommonClassNames.JAVA_UTIL_HASH_SET +
      "(" + CommonClassNames.JAVA_UTIL_ARRAYS + ".asList(new {1}[] '{'{0}'}')))";
    private static final String INITIALIZER_FORMAT_JAVA5 =
      CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSet(" +
      "new " + CommonClassNames.JAVA_UTIL_HASH_SET + "<{1}>" +
      "(" + CommonClassNames.JAVA_UTIL_ARRAYS + ".asList({0})))";
    private static final String INITIALIZER_FORMAT_JAVA9 = CommonClassNames.JAVA_UTIL_SET + ".of({0})";
    private static final String INITIALIZER_ENUM_SET =
      CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSet(" +
      "java.util.EnumSet.of({0}))";
  }
  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement element = getElement(editor, file);
    if (element == null) return IntentionPreviewInfo.EMPTY;
    extract(project, editor, element);
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    extract(project, editor, element);
  }

  private void extract(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    boolean preview = !element.isPhysical();
    List<ExpressionToConstantComparison> comparisons = comparisons(element).toList();
    if (comparisons.size() < 2) return;
    PsiClass containingClass = ClassUtils.getContainingStaticClass(element);
    if (containingClass == null) return;
    ExpressionToConstantReplacementContext context = new ExpressionToConstantReplacementContext(comparisons);
    List<ExpressionToConstantReplacementContext> copies = findCopies(comparisons, containingClass);
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    LinkedHashSet<String> suggestions = getSuggestions(comparisons);

    class Extractor implements ThrowableRunnable<RuntimeException> {
      SmartPsiElementPointer<PsiElement> resultPtr;
      SmartPsiElementPointer<PsiField> fieldPtr;

      @Override
      public void run() {
        if (!containingClass.isValid()) return;
        String name = manager.suggestUniqueVariableName(suggestions.iterator().next(), containingClass, false);
        String fieldInitializer = context.myInitializer;
        PsiType elementType = context.myTypePtr.getType();
        if (elementType == null) return;
        String pattern = getInitializer(elementType, containingClass);
        String initializer = MessageFormat.format(pattern, fieldInitializer, elementType.getCanonicalText());
        String modifiers = containingClass.isInterface() ? "" : "private static final ";
        String type = CommonClassNames.JAVA_UTIL_SET +
                      (PsiUtil.isLanguageLevel5OrHigher(containingClass) ? "<" + elementType.getCanonicalText() + ">" : "");
        PsiField field = factory.createFieldFromText(modifiers + type + " " + name + "=" + initializer + ";", containingClass);
        field = (PsiField)containingClass.add(field);
        RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(field);
        CodeStyleManager.getInstance(project).reformat(manager.shortenClassReferences(field));

        SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
        PsiElement result = context.replace(field);
        resultPtr = result == null ? null : smartPointerManager.createSmartPsiElementPointer(result);
        fieldPtr = smartPointerManager.createSmartPsiElementPointer(field);
      }
    }
    Extractor extractor = new Extractor();
    if (preview) {
      extractor.run();
    }
    else {
      WriteAction.run(extractor);
    }
    PsiElement result = extractor.resultPtr == null ? null : extractor.resultPtr.getElement();
    if (result == null || !result.isValid()) return;

    if (!copies.isEmpty() && !preview) {
      int answer = ApplicationManager.getApplication().isUnitTestMode() ? Messages.YES :
                   Messages.showYesNoDialog(project,
                                            JavaBundle.message("intention.extract.set.from.comparison.chain.duplicates",
                                                                      ApplicationNamesInfo.getInstance().getProductName(),
                                                                      copies.size()), JavaBundle.message(
                       "dialog.title.process.duplicates"),
                                            Messages.getQuestionIcon());
      if (answer == Messages.YES) {
        WriteAction.run(() -> {
          PsiField field = extractor.fieldPtr.getElement();
          if (field == null) return;
          for (ExpressionToConstantReplacementContext copy : copies) {
            copy.replace(field);
          }
        });
      }
    }

    PsiField field = extractor.fieldPtr.getElement();
    result = extractor.resultPtr.getElement();
    if (result == null || field == null) return;
    PsiReferenceExpression fieldRef =
      tryCast(ReferencesSearch.search(field, new LocalSearchScope(result)).findFirst(), PsiReferenceExpression.class);
    if (fieldRef == null) return;

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    editor.getCaretModel().moveToOffset(fieldRef.getTextOffset());
    editor.getSelectionModel().removeSelection();
    new MemberInplaceRenamer(field, field, editor).performInplaceRefactoring(suggestions);
  }

  private static List<ExpressionToConstantReplacementContext> findCopies(@NotNull List<ExpressionToConstantComparison> comparisons,
                                                                         @NotNull PsiClass aClass) {
    Set<String> orig = StreamEx.of(comparisons).map(c -> c.myConstantRepresentation).toSet();
    List<ExpressionToConstantReplacementContext> copies = new ArrayList<>();
    Set<PsiExpression> processedOperands = new HashSet<>();
    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
        super.visitPolyadicExpression(expression);
        if (!expression.getOperationTokenType().equals(JavaTokenType.OROR)) return;
        for (PsiExpression operand : expression.getOperands()) {
          if (processedOperands.contains(operand)) continue;
          List<ExpressionToConstantComparison> otherComparisons = comparisons(operand).toList();
          otherComparisons.stream().map(c -> c.myComparison).forEach(processedOperands::add);
          if (otherComparisons.size() == comparisons.size() &&
              otherComparisons.get(0).myExpression != comparisons.get(0).myExpression &&
              otherComparisons.get(0).myType.equals(comparisons.get(0).myType)
              && StreamEx.of(otherComparisons).map(c -> c.myConstantRepresentation).toSet().equals(orig)) {
            copies.add(new ExpressionToConstantReplacementContext(otherComparisons));
          }
        }
      }
    });
    return copies;
  }

  @NotNull
  String getInitializer(PsiType type, PsiClass containingClass) {
    if (!type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return Holder.INITIALIZER_ENUM_SET;
    }
    if (PsiUtil.isLanguageLevel9OrHigher(containingClass)) {
      return Holder.INITIALIZER_FORMAT_JAVA9;
    }
    if (JavaPsiFacade.getInstance(containingClass.getProject()).findClass(Holder.GUAVA_IMMUTABLE_SET, containingClass.getResolveScope()) != null) {
      return Holder.INITIALIZER_FORMAT_GUAVA;
    }
    if (PsiUtil.isLanguageLevel5OrHigher(containingClass)) {
      return Holder.INITIALIZER_FORMAT_JAVA5;
    }
    return Holder.INITIALIZER_FORMAT_JAVA2;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return comparisons(element).count() > 1;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.extract.set.from.comparison.chain.family");
  }

  @NotNull
  private static LinkedHashSet<String> getSuggestions(List<ExpressionToConstantComparison> comparisons) {
    PsiExpression stringExpression = comparisons.get(0).myExpression;
    Project project = stringExpression.getProject();
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo info = manager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, null, stringExpression,
                                                         comparisons.get(0).myType, false);
    // Suggestions like OBJECT and AN_OBJECT appear because Object.equals argument type is an Object,
    // such names are rarely appropriate
    LinkedHashSet<String> suggestions =
      StreamEx.of(info.names).without("OBJECT", "AN_OBJECT").map(StringUtil::pluralize).nonNull().toCollection(LinkedHashSet::new);
    Pair<String, String> prefixSuffix = comparisons.stream().map(cmp -> cmp.myConstantRepresentation).collect(
      MoreCollectors.pairing(MoreCollectors.commonPrefix(), MoreCollectors.commonSuffix(), Pair::create));
    StreamEx.of(prefixSuffix.first, prefixSuffix.second).flatMap(str -> StreamEx.split(str, "\\W+").limit(3))
      .map(str -> str.replaceFirst("^_+", "").replaceFirst("_+$", ""))
      .filter(str -> str.length() >= 3 && StringUtil.isJavaIdentifier(str))
      .flatMap(str -> StreamEx.of(manager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, str, null, null).names))
      .limit(5)
      .map(StringUtil::pluralize)
      .forEach(suggestions::add);
    if(comparisons.get(0).myType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      suggestions.add("STRINGS");
    }
    return suggestions;
  }

  private static StreamEx<ExpressionToConstantComparison> comparisons(PsiElement element) {
    PsiPolyadicExpression disjunction = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class);
    if (disjunction != null && disjunction.getOperationTokenType() == JavaTokenType.EQEQ) {
      disjunction = PsiTreeUtil.getParentOfType(disjunction, PsiPolyadicExpression.class);
    }
    if (disjunction == null || disjunction.getOperationTokenType() != JavaTokenType.OROR) return StreamEx.empty();
    PsiExpression[] operands = disjunction.getOperands();
    int offset = element.getTextOffset() - disjunction.getTextOffset();
    int index = IntStreamEx.ofIndices(operands, op -> op.getStartOffsetInParent() + op.getTextLength() > offset)
      .findFirst().orElse(operands.length - 1);
    ExpressionToConstantComparison anchorComparison = ExpressionToConstantComparison.create(operands[index]);
    if (anchorComparison == null) return StreamEx.empty();
    List<ExpressionToConstantComparison> prefix = IntStreamEx.rangeClosed(index - 1, 0, -1)
      .elements(operands)
      .map(ExpressionToConstantComparison::create)
      .takeWhile(anchorComparison::belongsToChain)
      .toList();
    List<ExpressionToConstantComparison> suffix = StreamEx.of(operands, index + 1, operands.length)
      .map(ExpressionToConstantComparison::create)
      .takeWhile(anchorComparison::belongsToChain)
      .toList();
    return StreamEx.ofReversed(prefix).append(anchorComparison).append(suffix);
  }

  static final class ExpressionToConstantReplacementContext {
    final SmartPsiElementPointer<PsiExpression> myExpressionPtr;
    final SmartPsiElementPointer<PsiExpression> myFirstComparisonPtr;
    final SmartPsiElementPointer<PsiExpression> myLastComparisonPtr;
    final SmartTypePointer myTypePtr;
    final String myInitializer;

    ExpressionToConstantReplacementContext(List<ExpressionToConstantComparison> comparisons) {
      assert !comparisons.isEmpty();
      Project project = comparisons.get(0).myComparison.getProject();
      SmartPointerManager manager = SmartPointerManager.getInstance(project);
      myExpressionPtr = manager.createSmartPsiElementPointer(comparisons.get(0).myExpression);
      myFirstComparisonPtr = manager.createSmartPsiElementPointer(comparisons.get(0).myComparison);
      myLastComparisonPtr = manager.createSmartPsiElementPointer(comparisons.get(comparisons.size() - 1).myComparison);
      myTypePtr = SmartTypePointerManager.getInstance(project).createSmartTypePointer(comparisons.get(0).myType);
      myInitializer = StreamEx.of(comparisons).map(cmp -> cmp.myConstant.getText()).joining(",");
    }

    @Nullable
    private PsiElement replace(@NotNull PsiField field) {
      Project project = field.getProject();
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) return null;
      String name = field.getName();
      PsiExpression expression = myExpressionPtr.getElement();
      PsiExpression firstComparison = myFirstComparisonPtr.getElement();
      PsiExpression lastComparison = myLastComparisonPtr.getElement();
      if (expression == null || firstComparison == null || lastComparison == null) return null;
      PsiExpression disjunction = tryCast(firstComparison.getParent(), PsiPolyadicExpression.class);
      if (disjunction == null) return null;
      int startOffset = firstComparison.getStartOffsetInParent();
      int endOffset = lastComparison.getStartOffsetInParent() + lastComparison.getTextLength();
      String origText = disjunction.getText();
      String fieldReference = PsiResolveHelper.getInstance(project).resolveReferencedVariable(name, disjunction) == field ?
                              name : containingClass.getQualifiedName() + "." + name;
      String replacementText = origText.substring(0, startOffset) +
                               fieldReference + ".contains(" + expression.getText() + ")" +
                               origText.substring(endOffset);
      PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacementText, disjunction);
      if (replacement instanceof PsiMethodCallExpression && disjunction.getParent() instanceof PsiParenthesizedExpression) {
        disjunction = (PsiExpression)disjunction.getParent();
      }
      return disjunction.replace(replacement);
    }
  }

  static final class ExpressionToConstantComparison {
    @NotNull final PsiExpression myComparison;
    @NotNull final PsiExpression myExpression;
    @NotNull final PsiExpression myConstant;
    @NotNull final PsiType myType;
    @NotNull final String myConstantRepresentation;

    ExpressionToConstantComparison(@NotNull PsiExpression comparison,
                                   @NotNull PsiExpression expression,
                                   @NotNull PsiExpression constant,
                                   @NotNull String constantRepresentation) {
      myComparison = comparison;
      myExpression = expression;
      myConstant = constant;
      myType = Objects.requireNonNull(constant.getType());
      myConstantRepresentation = constantRepresentation;
    }

    boolean belongsToChain(@Nullable ExpressionToConstantComparison other) {
      return other != null && PsiEquivalenceUtil.areElementsEquivalent(myExpression, other.myExpression) && myType.equals(other.myType);
    }

    static ExpressionToConstantComparison create(PsiExpression candidate) {
      candidate = PsiUtil.skipParenthesizedExprDown(candidate);
      EqualityCheck check = EqualityCheck.from(candidate);
      if (check != null) {
        return fromComparison(candidate, check.getLeft(), check.getRight());
      }
      PsiBinaryExpression binOp = tryCast(candidate, PsiBinaryExpression.class);
      if (binOp != null && JavaTokenType.EQEQ.equals(binOp.getOperationTokenType())) {
        return fromComparison(candidate, binOp.getLOperand(), binOp.getROperand());
      }
      return null;
    }

    @Nullable
    private static ExpressionToConstantComparison fromComparison(PsiExpression candidate, PsiExpression left, PsiExpression right) {
      if (left == null || right == null) return null;
      ExpressionToConstantComparison fromLeft = tryExtract(candidate, left, right);
      if (fromLeft != null) return fromLeft;
      return tryExtract(candidate, right, left);
    }

    @Nullable
    private static ExpressionToConstantComparison tryExtract(PsiExpression candidate, PsiExpression constant, PsiExpression nonConstant) {
      String constantValue = tryCast(ExpressionUtils.computeConstantExpression(constant), String.class);
      if (constantValue != null) {
        return new ExpressionToConstantComparison(candidate, nonConstant, constant, constantValue);
      }
      PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(constant), PsiReferenceExpression.class);
      if (ref != null) {
        PsiEnumConstant enumConstant = tryCast(ref.resolve(), PsiEnumConstant.class);
        if (enumConstant != null) {
          return new ExpressionToConstantComparison(candidate, nonConstant, ref, enumConstant.getName());
        }
      }
      return null;
    }
  }
}
