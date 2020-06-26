// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Danila Ponomarenko
 */
public class RecursiveCallLineMarkerProvider extends LineMarkerProviderDescriptor {

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    return null; //do nothing
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                     @NotNull Collection<? super LineMarkerInfo<?>> result) {
    final Set<PsiStatement> statements = new HashSet<>();

    for (PsiElement element : elements) {
      ProgressManager.checkCanceled();
      if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
        final PsiStatement statement = PsiTreeUtil.getParentOfType(methodCall, PsiStatement.class, true, PsiMethod.class);
        if (!statements.contains(statement) && isRecursiveMethodCall(methodCall)) {
          statements.add(statement);
          ContainerUtil.addIfNotNull(result, RecursiveMethodCallMarkerInfo.create(methodCall));
        }
      }
    }
  }

  public static boolean isRecursiveMethodCall(@NotNull PsiMethodCallExpression methodCall) {
    final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
      return false;
    }

    final PsiMethod method = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true, PsiLambdaExpression.class, PsiClass.class);
    if (method == null || !method.getName().equals(methodCall.getMethodExpression().getReferenceName())) {
      return false;
    }

    return Comparing.equal(method, methodCall.resolveMethod());
  }

  @NotNull
  @Override
  public String getName() {
    return JavaBundle.message("line.marker.recursive.call");
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Gutter.RecursiveMethod;
  }

  private static final class RecursiveMethodCallMarkerInfo extends LineMarkerInfo<PsiElement> {
    private static RecursiveMethodCallMarkerInfo create(@NotNull PsiMethodCallExpression methodCall) {
      PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
      if (nameElement != null) {
        return new RecursiveMethodCallMarkerInfo(nameElement);
      }
      return null;
    }

    private RecursiveMethodCallMarkerInfo(@NotNull PsiElement name) {
      super(name, name.getTextRange(), AllIcons.Gutter.RecursiveMethod, FunctionUtil.constant("Recursive call"), null,
            GutterIconRenderer.Alignment.RIGHT);
    }

    @Override
    public GutterIconRenderer createGutterRenderer() {
      if (myIcon == null) return null;
      return new LineMarkerGutterIconRenderer<PsiElement>(this){
        @Override
        public AnAction getClickAction() {
          return null; // to place breakpoint on mouse click
        }
      };
    }
  }
}

