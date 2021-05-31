// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

final class IntroduceVariableTargetBlockChooser {
  private static final Logger LOG = Logger.getInstance(IntroduceVariableTargetBlockChooser.class);

  /**
   * Allow to introduce variable above expression lambda if extracted expression doesn't depend on context (lambda parameters)
   * 
   * @param editor   to show popup above
   * @param anchor   nearest anchor
   * @param expr     variable initializer
   * @param callback extraction job
   */
  static void chooseTargetAndPerform(Editor editor, PsiElement anchor, PsiExpression expr, Pass<PsiElement> callback) {
    List<? extends PsiElement> containers = getContainers(anchor, expr);
    if (containers.size() == 1 ) {
      callback.accept(anchor);
    }
    else {
      IntroduceTargetChooser.showChooser(editor, containers, callback,
                                         element -> {
                                           PsiElement container = takeNextContainer(containers, element);
                                           if (container instanceof PsiLambdaExpression) {
                                             PsiType type = ((PsiLambdaExpression)container).getFunctionalInterfaceType();
                                             return (type != null ? type.getPresentableText() + ": " : "") + PsiExpressionTrimRenderer.render((PsiExpression)container);
                                           }
                                           return JavaBundle.message("target.code.block.presentable.text");
                                         }, JavaBundle.message("popup.title.select.target.code.block"),
                                         element -> {
                                           PsiElement result = takeNextContainer(containers, element);
                                           return result instanceof PsiLambdaExpression ? Objects.requireNonNull(((PsiLambdaExpression)result).getBody()).getTextRange()
                                                                                        : result.getTextRange();
                                         });
    }
  }

  @NotNull
  private static PsiElement takeNextContainer(List<? extends PsiElement> containers, PsiElement element) {
    int i = containers.indexOf(element) + 1;
    if (i < containers.size()) {
      return containers.get(i);
    }
    PsiElement lastItem = ContainerUtil.getLastItem(containers);
    LOG.assertTrue(lastItem instanceof PsiLambdaExpression);
    PsiElement parent = PsiTreeUtil.getParentOfType(lastItem, PsiStatement.class, PsiField.class);
    return parent != null ? parent.getParent() : lastItem.getParent();
  }

   private static List<PsiElement> getContainers(PsiElement anchor, PsiExpression expr) {
    List<PsiElement> containers = new ArrayList<>();
    containers.add(anchor);
    PsiElement container = PsiTreeUtil.getParentOfType(anchor, PsiLambdaExpression.class, true, PsiCodeBlock.class);
    if (container == null) return containers;
    Set<PsiElement> dependencies = 
      PsiTreeUtil.collectElementsOfType(expr, PsiReferenceExpression.class)
        .stream()
        .map(ref -> ref.resolve())
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    while (container instanceof PsiLambdaExpression) {
      if (ContainerUtil.intersects(dependencies, Arrays.asList(((PsiLambdaExpression)container).getParameterList().getParameters()))) {
        break;
      }
      containers.add(container);
      container = container.getParent();
    }
    return containers;
  }
}
