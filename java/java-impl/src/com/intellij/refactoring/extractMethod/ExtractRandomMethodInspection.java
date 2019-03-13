// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
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
      for (int attempt = 0; attempt < 15; attempt++) {
        if (codeBlocks.isEmpty()) {
          return null;
        }
        PsiCodeBlock codeBlock = fetchCodeBlock(codeBlocks);
        if (codeBlock != null) {
          PsiElement[] elements = getRandomElements(codeBlock.getStatements());
          if (elements.length != 0 && !isTooSimple(elements)) {
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

  private static boolean isTooSimple(PsiElement[] elements) {
    assert elements.length != 0;

    if (elements[0].getParent() instanceof PsiCodeBlock) {
      PsiCodeBlock codeBlock = (PsiCodeBlock)elements[0].getParent();
      if (codeBlock.getParent() instanceof PsiParameterListOwner) {
        PsiStatement[] statements = codeBlock.getStatements();
        if (statements[0] == elements[0] && statements[statements.length - 1] == elements[elements.length - 1]) {
          return true; // the whole method's body
        }
      }
    }

    if (elements.length == 1) {
      PsiElement element = elements[0];
      if (element instanceof PsiReturnStatement || element instanceof PsiThrowStatement) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiCodeBlock fetchCodeBlock(List<PsiCodeBlock> codeBlocks) {
    if (codeBlocks.isEmpty()) {
      return null;
    }
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int index = random.nextInt(codeBlocks.size());
    PsiCodeBlock codeBlock = codeBlocks.get(index);
    if (hasKnownIssue(codeBlock)) {
      codeBlocks.remove(index);
      return null;
    }
    return codeBlock;
  }

  private static boolean hasKnownIssue(@NotNull PsiCodeBlock codeBlock) {
    PsiElement parent = codeBlock.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      if (method.isConstructor()) {
        Ref<Boolean> refFound = new Ref<>(Boolean.FALSE);

        codeBlock.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (PsiUtil.isOnAssignmentLeftHand(expression)) {
              PsiElement resolved = expression.resolve();
              if (resolved instanceof PsiField) {
                PsiField field = (PsiField)resolved;
                if (field.getContainingClass() == method.getContainingClass() &&
                    field.hasModifierProperty(PsiModifier.FINAL) && !field.hasModifierProperty(PsiModifier.STATIC)) {
                  refFound.set(Boolean.TRUE);
                  stopWalking();
                }
              }
            }
          }

          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            String name = expression.getMethodExpression().getReferenceName();
            if (PsiKeyword.THIS.equals(name) || PsiKeyword.SUPER.equals(name)) {
              refFound.set(Boolean.TRUE);
              stopWalking();
            }
          }
        });
        return refFound.get();
      }
    }
    return false;
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
      PsiElement[] elements = getRandomElements(codeBlocks);
      if (elements.length != 0) {
        return elements;
      }
    }

    if (statement instanceof PsiIfStatement) {
      PsiStatement thenBranch = ((PsiIfStatement)statement).getThenBranch();
      PsiStatement elseBranch = ((PsiIfStatement)statement).getElseBranch();
      List<PsiCodeBlock> codeBlocks = ContainerUtil.mapNotNull(
        Arrays.asList(thenBranch, elseBranch), s -> s instanceof PsiBlockStatement ? ((PsiBlockStatement)s).getCodeBlock() : null);
      PsiElement[] elements = getRandomElements(codeBlocks);
      if (elements.length != 0) {
        return elements;
      }
    }

    return new PsiElement[]{statement};
  }

  @NotNull
  private PsiElement[] getRandomElements(@NotNull List<PsiCodeBlock> codeBlocks) {
    if (codeBlocks.isEmpty()) {
      return PsiElement.EMPTY_ARRAY;
    }
    ThreadLocalRandom random = ThreadLocalRandom.current();
    PsiCodeBlock codeBlock = codeBlocks.get(random.nextInt(codeBlocks.size()));
    return getRandomElements(codeBlock.getStatements());
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
