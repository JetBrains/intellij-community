// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The goal of this test is to check that IDEA supports popular cases of JSpecify annotations and doesn't have regressions.
 * This test contains a set of filters, which are used to filter out some cases that are not supported by IDEA or aren't expected to be supported.
 * If it is necessary to check the full set of tests, please, use {@link JSpecifyAnnotationTest}.
 */
public class JSpecifyFilteredAnnotationTest extends JSpecifyAnnotationTest {
  private static final List<ErrorFilter> FILTERS = List.of(
    new SkipErrorFilter("jspecify_nullness_not_enough_information"), //it is useless for our goals
    new ReturnSynchronizedWithUnspecifiedFilter(), // it looks like it is useless because @Unspecified is not supported
    new SkipIndividuallyFilter( //each case has its own reason (line number starts from 0)
      Set.of(
        new Pair<>("CastWildcardToTypeVariable.java", 21),  // see: IDEA-377686

        new Pair<>("ContravariantReturns.java", 32),  // see: IDEA-377687
        new Pair<>("ContravariantReturns.java", 36),  // see: IDEA-377687
        new Pair<>("ExtendsTypeVariableImplementedForNullableTypeArgument.java",
                   28), // overriding method with @NotNull, original has @Nullable, but IDEA doesn't highlight the opposite example, see IDEA-377687
        new Pair<>("ExtendsTypeVariableImplementedForNullableTypeArgument.java",
                   33), // overriding method with @NotNull, original has @Nullable, but IDEA doesn't highlight the opposite example, see IDEA-377687
        new Pair<>("OverrideParameters.java", 66),  // see: IDEA-377687

        new Pair<>("DereferenceTypeVariable.java", 117),  // see: IDEA-377688
        new Pair<>("TypeVariableToObject.java", 104), // see: IDEA-377688

        new Pair<>("NullLiteralToTypeVariable.java", 58), // see: IDEA-377691
        new Pair<>("NullLiteralToTypeVariable.java", 78), // see: IDEA-377691
        new Pair<>("NullLiteralToTypeVariable.java", 98), // see: IDEA-377691
        new Pair<>("NullLiteralToTypeVariable.java", 103), // see: IDEA-377691
        new Pair<>("NullLiteralToTypeVariable.java", 118), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToParent.java", 88), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToParent.java", 98), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToSelf.java", 58), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToSelf.java", 78), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToSelf.java", 103), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToSelf.java", 118), // see: IDEA-377691
        new Pair<>("TypeVariableToParent.java", 94), // see: IDEA-377691

        new Pair<>("NullUnmarkedUndoesNullMarkedForWildcards.java", 23), // see: IDEA-377693

        new Pair<>("SuperObject.java", 31),  // see: IDEA-377694
        new Pair<>("SuperTypeVariable.java", 28),  // see: IDEA-377694
        new Pair<>("SuperTypeVariable.java", 57),  // see: IDEA-377694
        new Pair<>("SuperTypeVariable.java", 86),  // see: IDEA-377694
        new Pair<>("SuperTypeVariable.java", 115), // see: IDEA-377694

        new Pair<>("UninitializedField.java", 29), // see: IDEA-377695

        new Pair<>("ContainmentExtends.java", 27),  // see: IDEA-377696
        new Pair<>("ContainmentSuper.java", 36),  // see: IDEA-377696
        new Pair<>("ContainmentSuperVsExtends.java", 22),  // see: IDEA-377696
        new Pair<>("ContainmentSuperVsExtendsSameType.java", 21),  // see: IDEA-377696

        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 62), // see: IDEA-377697

        new Pair<>("WildcardCapturesToBoundOfTypeParameterNotToTypeVariableItself.java", 24), // see: IDEA-377699

        new Pair<>("UnionTypeArgumentWithUseSite.java", 30), // see: IDEA-377706

        new Pair<>("SelfType.java", 34),  // see: IDEA-377707
        new Pair<>("SelfType.java", 43),  // see: IDEA-377707
        new Pair<>("OutOfBoundsTypeVariable.java", 21)  // see: IDEA-377707
      )
    ),
    new SkipIndividuallyFilter( //cases to investigate. (line number starts from 0)
      Set.of(
        new Pair<>("AnnotatedBoundsOfWildcard.java", 74), // @unspecified, skip
        new Pair<>("AnnotatedBoundsOfWildcard.java", 76), // @unspecified, skip
        new Pair<>("ComplexParametric.java", 158), // @unspecified, skip
        new Pair<>("ComplexParametric.java", 172), // @unspecified, skip
        new Pair<>("ComplexParametric.java", 176), // @unspecified, skip
        new Pair<>("ComplexParametric.java", 238), // @unspecified, skip
        new Pair<>("ComplexParametric.java", 246), // @unspecified, skip
        new Pair<>("DereferenceTypeVariable.java", 123), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableToObject.java", 43), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableToObject.java", 52), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableToOther.java", 43), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableToOther.java", 52), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 27), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 37), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 42), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 47), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 57), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableUnspecToObject.java", 63), // @unspecified, skip
        new Pair<>("MultiBoundTypeVariableUnspecToOther.java", 63), // @unspecified, skip
        new Pair<>("NotNullMarkedUseOfTypeVariable.java", 41), // @unspecified, skip
        new Pair<>("NullLiteralToTypeVariable.java", 53), // @unspecified, skip
        new Pair<>("NullLiteralToTypeVariable.java", 73), // @unspecified, skip
        new Pair<>("NullLiteralToTypeVariable.java", 83), // @unspecified, skip
        new Pair<>("NullLiteralToTypeVariable.java", 93), // @unspecified, skip
        new Pair<>("NullLiteralToTypeVariable.java", 113), // @unspecified, skip
        new Pair<>("TypeVariableToObject.java", 109), // @unspecified, skip
        new Pair<>("TypeVariableToParent.java", 80), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToParent.java", 73), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToParent.java", 78), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToParent.java", 83), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToParent.java", 93), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToSelf.java", 53), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToSelf.java", 73), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToSelf.java", 83), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToSelf.java", 93), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToSelf.java", 98), // @unspecified, skip
        new Pair<>("TypeVariableUnionNullToSelf.java", 113), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToObject.java", 58), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToObject.java", 78), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToObject.java", 98), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToObject.java", 103), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToObject.java", 108), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToObject.java", 113), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToObject.java", 118), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToParent.java", 53), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToParent.java", 68), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToParent.java", 83), // @unspecified, skip
        new Pair<>("TypeVariableUnspecToParent.java", 98), // @unspecified, skip
        new Pair<>("UnionTypeArgumentWithUseSite.java", 77), // @unspecified, skip
        new Pair<>("UnionTypeArgumentWithUseSite.java", 95) // @unspecified, skip
      )
    ) {
      @Override
      public boolean shouldCount() {
        return false;
      }
    },
    new CallWithParameterWithNestedGenericsFilter(), // see: IDEA-377682
    new VariableWithNestedGenericsFilter(), // see: IDEA-377683
    new ReturnWithNestedGenericsFilter() // see: IDEA-375132
  );

  @Override
  protected List<ErrorFilter> getErrorFilter() {
    return FILTERS;
  }
  
  @AfterClass
  public static void reportUnusedFilters() {
    FILTERS.forEach(ErrorFilter::reportUnused);
  }

  private static class CallWithParameterWithNestedGenericsFilter implements ErrorFilter {

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiElement element = findElement(file, strippedText, lineNumber, startLineOffset);
      PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class, true);
      if (statement == null) return false;
      PsiExpression expression = statement.getExpression();
      if (!(expression instanceof PsiCallExpression callExpression)) return false;
      PsiMethod method = callExpression.resolveMethod();
      if (method == null) return false;
      return ContainerUtil.exists(method.getParameterList().getParameters(),
                                  parameter -> parameter.getType() instanceof PsiClassType classType && classType.hasParameters());
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      //filter only actual file
      return false;
    }
  }

  private static class VariableWithNestedGenericsFilter implements ErrorFilter {

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiElement element = findElement(file, strippedText, lineNumber, startLineOffset);
      PsiVariable variable = PsiTreeUtil.getParentOfType(element, PsiVariable.class, true);
      if (variable == null) return false;
      return variable.getType() instanceof PsiClassType classType && classType.hasParameters();
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      //filter only actual file
      return false;
    }
  }

  private static class ReturnWithNestedGenericsFilter implements ErrorFilter {

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiElement element = findElement(file, strippedText, lineNumber, startLineOffset);
      PsiReturnStatement returnStatement = PsiTreeUtil.getParentOfType(element, PsiReturnStatement.class, true);
      if (returnStatement == null) return false;
      PsiMethod method = PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class, true);
      return method.getReturnType() instanceof PsiClassType classType && classType.hasParameters();
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      //filter only actual file
      return false;
    }
  }


  private static class SkipIndividuallyFilter implements ErrorFilter {
    private final Set<Pair<String, Integer>> places;
    private final Set<Pair<String, Integer>> unusedPlaces;

    private SkipIndividuallyFilter(Set<Pair<String, Integer>> places) { 
      this.places = places;
      this.unusedPlaces = new HashSet<>(places);
    }

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      return filter(Pair.create(file.getName(), lineNumber));
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiFile file = psiElement.getContainingFile();
      if (file == null) return false;
      Document document = file.getFileDocument();
      return filter(Pair.create(file.getName(), document.getLineNumber(psiElement.getTextRange().getStartOffset()) - 1));
    }

    private boolean filter(Pair<@NotNull @NlsSafe String, Integer> pair) {
      if (places.contains(pair)) {
        unusedPlaces.remove(pair);
        return true;
      }
      return false;
    }

    @Override
    public void reportUnused() {
      if (unusedPlaces.isEmpty()) return;
      System.out.println("Some filters were unused; probably they are not actual anymore and should be excluded:\n"
                         +StringUtil.join(unusedPlaces, "\n"));
    }
  }

  private static class ReturnSynchronizedWithUnspecifiedFilter implements ErrorFilter {

    @Override
    public boolean shouldCount() {
      return false;
    }

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiElement element = findElement(file, strippedText, lineNumber, startLineOffset);
      return filterExpected(element, errorMessage);
    }


    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      PsiStatement statement = PsiTreeUtil.getParentOfType(psiElement, PsiReturnStatement.class, PsiSynchronizedStatement.class);
      if (statement == null) return false;
      Collection<PsiReferenceExpression> children = PsiTreeUtil.findChildrenOfAnyType(statement, PsiReferenceExpression.class);
      return ContainerUtil.exists(children, child -> {
        PsiElement resolved = child.resolve();
        if (resolved instanceof PsiVariable variable){
          PsiTypeElement typeElement = variable.getTypeElement();
          return hasUnspecified(typeElement);
        }
        if (resolved instanceof PsiMethod method) {
          return hasUnspecified(method.getReturnTypeElement());
        }
        return false;
      });
    }
  }

  private static boolean hasUnspecified(@Nullable PsiTypeElement element) {
    if (element == null) return false;
    return ContainerUtil.exists(PsiTreeUtil.findChildrenOfType(element, PsiAnnotation.class),
                                a -> a.getText().contains("Unspecified"));
  }

  private static @Nullable PsiElement findElement(@NotNull PsiFile file,
                                                  @NotNull String strippedText,
                                                  int lineNumber,
                                                  int startLineOffset) {
    return file.findElementAt(StringUtil.lineColToOffset(strippedText, lineNumber + 1, startLineOffset) + 1);
  }

  private static class SkipErrorFilter implements ErrorFilter {
    private final String myMessage;


    private SkipErrorFilter(@NotNull String message) {
      myMessage = message;
    }

    @Override
    public boolean shouldCount() {
      return false;
    }

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      return errorMessage.contains(myMessage);
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      return errorMessage.contains(myMessage);
    }
  }
}
