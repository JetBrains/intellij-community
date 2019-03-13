// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
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
  private static final Logger LOG = Logger.getInstance(ExtractRandomMethodInspection.class);

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
            JavaDuplicatesExtractMethodProcessor processor = createProcessor(elements);
            if (processor != null) {
              ProblemDescriptor descriptor = manager.createProblemDescriptor(elements[0], elements[elements.length - 1],
                                                                             "May extract random method",
                                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
                                                                             new ExtractRandomMethodFix());
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
    if (parent instanceof PsiMethod && ((PsiMethod)parent).isConstructor() || parent instanceof PsiClassInitializer) {
      PsiClass containingClass = ((PsiMember)parent).getContainingClass();
      Ref<Boolean> refFound = new Ref<>(Boolean.FALSE);

      codeBlock.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          if (PsiUtil.isOnAssignmentLeftHand(expression)) {
            PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiField) {
              PsiField field = (PsiField)resolved;
              if (field.getContainingClass() == containingClass && field.hasModifierProperty(PsiModifier.FINAL)) {
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
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Extract random method";
    }

    @Override
    public void applyFix(@NotNull Project project,
                         @NotNull ProblemDescriptor descriptor) {
      PsiElement startElement = descriptor.getStartElement();
      PsiElement endElement = descriptor.getEndElement();
      PsiFile containingFile = startElement.getContainingFile();

      int startOffset = startElement.getTextRange().getStartOffset();
      int endOffset = endElement.getTextRange().getEndOffset();
      String startText = ArrayUtil.getFirstElement(startElement.getText().split("\n"));
      String endText = startElement != endElement ? ArrayUtil.getFirstElement(endElement.getText().split("\n")) : "";

      try {
        List<PsiElement> elements = new ArrayList<>();
        for (PsiElement element = startElement; element != null; element = element.getNextSibling()) {
          elements.add(element);
          if (element == endElement) break;
        }
        JavaDuplicatesExtractMethodProcessor processor = createProcessor(elements.toArray(PsiElement.EMPTY_ARRAY));
        if (processor == null) {
          LOG.warn("Failed to prepare");
          logDetails(containingFile, startOffset, endOffset, startText, endText);
          return;
        }

        processor.applyDefaults("randomMethod", PsiModifier.PRIVATE);
        ExtractMethodHandler.extractMethod(project, processor);
      }
      catch (Exception e) {
        LOG.error("Failed to extract method", e);
        logDetails(containingFile, startOffset, endOffset, startText, endText);
      }
    }

    private static void logDetails(PsiFile containingFile, int startOffset, int endOffset, String startText, String endText) {
      LOG.warn("File: " + containingFile);
      LOG.warn("TextRange " + startOffset + "-" + endOffset);
      if (startText != null && !startText.isEmpty()) LOG.warn(startText);
      if (endText != null && !endText.isEmpty()) LOG.warn(endText);
    }
  }

  @Nullable
  private static JavaDuplicatesExtractMethodProcessor createProcessor(PsiElement[] elements) {
    JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(elements, "Extract random method");
    return processor.prepare(false) ? processor : null;
  }
}
