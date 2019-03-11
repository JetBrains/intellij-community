// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Pavel.Dolgov
 */
public class ExtractRandomMethodInspection extends LocalInspectionTool {
  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isOnTheFly || !(file instanceof PsiJavaFile)) {
      return null;
    }
    PsiClass[] classes = ((PsiJavaFile)file).getClasses();
    if (classes.length != 0) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      PsiClass psiClass = classes[random.nextInt(classes.length)];
      PsiClassInitializer[] initializers = psiClass.getInitializers();
      PsiMethod[] methods = psiClass.getMethods();
      List<PsiCodeBlock> codeBlocks =
        Stream.concat(Arrays.stream(initializers).map(PsiClassInitializer::getBody),
                      Arrays.stream(methods).map(PsiMethod::getBody))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      if (!codeBlocks.isEmpty()) {
        for (int attempt = 0; attempt < 10; attempt++) {
          PsiCodeBlock codeBlock = codeBlocks.get(random.nextInt(codeBlocks.size()));
          PsiElement[] elements = getRandomElements(codeBlock.getStatements());
          if (elements.length != 0) {
            JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(elements, "Extract random method");
            if (processor.prepare(false)) {
              PsiElement parent = elements[0].getParent();
              int start = elements[0].getTextRangeInParent().getStartOffset();
              int end = elements[elements.length - 1].getTextRangeInParent().getEndOffset();
              ProblemDescriptor descriptor = manager.createProblemDescriptor(parent, new TextRange(start, end), "May extract random method",
                                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
                                                                             new ExtractRandomMethodFix(elements.length));
              return new ProblemDescriptor[]{descriptor};
            }
          }
        }
      }
    }

    return null;
  }

  @NotNull
  PsiElement[] getRandomElements(PsiStatement[] statements) {
    if (statements.length == 0) {
      return PsiElement.EMPTY_ARRAY;
    }
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int a = random.nextInt(statements.length), b = random.nextInt(statements.length);
    int start = Math.min(a, b), end = Math.max(a, b);
    if (start != end) {
      PsiElement[] elements = new PsiElement[end - start + 1];
      System.arraycopy(statements, start, elements, 0, end - start + 1);
      return elements;
    }
    PsiStatement statement = statements[start];
    if (random.nextInt(10) == 0) {
      return new PsiElement[]{statement};
    }

    if (statement instanceof PsiLoopStatement) {
      PsiStatement body = ((PsiLoopStatement)statement).getBody();
      if (body instanceof PsiBlockStatement) {
        PsiElement[] elements = getRandomElements(((PsiBlockStatement)body).getCodeBlock().getStatements());
        if (elements.length != 0) {
          return elements;
        }
      }
    }

    if (statement instanceof PsiTryStatement) {
      List<PsiCodeBlock> codeBlocks = new ArrayList<>();
      PsiTryStatement tryStatement = (PsiTryStatement)statement;
      ContainerUtil.addIfNotNull(codeBlocks, tryStatement.getTryBlock());
      ContainerUtil.addIfNotNull(codeBlocks, tryStatement.getFinallyBlock());
      ContainerUtil.addAllNotNull(codeBlocks, tryStatement.getCatchBlocks());
      PsiCodeBlock codeBlock = codeBlocks.get(random.nextInt(codeBlocks.size()));
      PsiElement[] elements = getRandomElements(codeBlock.getStatements());
      if (elements.length != 0) {
        return elements;
      }
    }

    if (statement instanceof PsiIfStatement) {
      PsiStatement thenBranch = ((PsiIfStatement)statement).getThenBranch();
      PsiStatement elseBranch = ((PsiIfStatement)statement).getElseBranch();
      List<PsiCodeBlock> codeBlocks = ContainerUtil.mapNotNull(
        Arrays.asList(thenBranch, elseBranch), s -> s instanceof PsiBlockStatement ? ((PsiBlockStatement)s).getCodeBlock() : null);
      if (!codeBlocks.isEmpty()) {
        PsiCodeBlock codeBlock = codeBlocks.get(random.nextInt(codeBlocks.size()));
        PsiElement[] elements = getRandomElements(codeBlock.getStatements());
        if (elements.length != 0) {
          return elements;
        }
      }
    }

    return new PsiElement[]{statement};
  }

  private static class ExtractRandomMethodFix implements LocalQuickFix {
    private final int myLength;

    ExtractRandomMethodFix(int length) {
      myLength = length;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return myLength != 1 ? "Extract random method from " + myLength + " statements" : "Extract random method from the statement";
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Extract random method";
    }

    @Override
    public void applyFix(@NotNull Project project,
                         @NotNull ProblemDescriptor descriptor) {

    }
  }
}
