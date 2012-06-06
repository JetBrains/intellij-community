/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.RecursionUtil;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * @author Danila Ponomarenko
 */
public class RecursiveCallLineMarkerProvider implements LineMarkerProvider, DumbAware {
  private static final Icon RECURSIVE_METHOD_ICON = AllIcons.Gutter.RecursiveMethod;

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null; //do nothing
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
    final Set<PsiMethod> recMethods = new HashSet<PsiMethod>();

    for (PsiElement element : elements) {
      if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && !recMethods.contains(method) && RecursionUtil.isRecursiveMethodCall(methodCall)) {
          recMethods.add(method);
          result.add(RecursiveMethodMarkerInfo.create(method));
        }
      }
    }
  }

  @NotNull
  private static List<PsiMethodCallExpression> getRecursiveMethodCalls(final @NotNull PsiMethod method) {
    final List<PsiMethodCallExpression> result = new ArrayList<PsiMethodCallExpression>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        method.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            if (RecursionUtil.isRecursiveMethodCall(expression)) {
              result.add(expression);
            }
            super.visitMethodCallExpression(expression);
          }
        });
      }
    });
    return result;
  }

  private static class RecursiveMethodMarkerInfo extends LineMarkerInfo<PsiMethod> {
    public RecursiveMethodMarkerInfo(PsiMethod method,
                                     TextRange range,
                                     Icon icon,
                                     int markers,
                                     Function<PsiMethod, String> constant,
                                     GutterIconNavigationHandler<PsiMethod> handler,
                                     GutterIconRenderer.Alignment left) {
      super(method, range, icon, markers, constant, handler, left);
    }

    @NotNull
    public static RecursiveMethodMarkerInfo create(@NotNull PsiMethod method){
      final PsiIdentifier identifier = method.getNameIdentifier();
      return new RecursiveMethodMarkerInfo(method,
                                           identifier != null ? identifier.getTextRange() : method.getTextRange(),
                                           RECURSIVE_METHOD_ICON,
                                           Pass.UPDATE_OVERRIDEN_MARKERS,
                                           FunctionUtil.<PsiMethod, String>constant("Potentially recursive method"),
                                           getNavigationHandler(method),
                                           GutterIconRenderer.Alignment.LEFT
      );
    }


    private static GutterIconNavigationHandler<PsiMethod> getNavigationHandler(final @NotNull PsiMethod method) {
      return new GutterIconNavigationHandler<PsiMethod>() {
        @Override
        public void navigate(@NotNull MouseEvent e, PsiMethod elt) {
          final List<PsiMethodCallExpression> calls = getRecursiveMethodCalls(method);
          if (calls.size() == 1) {
            navigateToMethodCall(calls.get(0));
          }
          else {
            showPopup(e, calls);
          }
        }
      };
    }

    private static void showPopup(@NotNull MouseEvent e, @NotNull List<PsiMethodCallExpression> calls) {
      final JBList list = new JBList(calls);
      list.setFixedCellHeight(20);
      list.installCellRenderer(createCellRenderer());
      JBPopupFactory.getInstance().
        createListPopupBuilder(list).
        setItemChoosenCallback(createItemChosenCallback(e, list)).
        createPopup().show(new RelativePoint(e));
    }


    @NotNull
    private static Runnable createItemChosenCallback(final MouseEvent e, final @NotNull JBList list) {
      return new Runnable() {
        @Override
        public void run() {
          final Object value = list.getSelectedValue();
          if (!(value instanceof PsiMethodCallExpression)) {
            return;
          }
          navigateToMethodCall((PsiMethodCallExpression)value);
        }
      };
    }

    private static void navigateToMethodCall(@NotNull PsiMethodCallExpression methodCall) {
      final PsiIdentifier identifier = PsiTreeUtil.getChildOfType(methodCall.getMethodExpression(), PsiIdentifier.class);
      if (identifier != null) {
        navigateTo(identifier);
      }else {
        navigateTo(methodCall);
      }
    }

    private static void navigateTo(@NotNull PsiElement element) {
      final Editor editor = PsiUtilBase.findEditor(element);
      if (editor == null) return;

      editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }


    @NotNull
    private static NotNullFunction<Object, JComponent> createCellRenderer() {
      return new NotNullFunction<Object, JComponent>() {
        @NotNull
        @Override
        public JComponent fun(Object o) {
          if (!(o instanceof PsiMethodCallExpression)) {
            return new JBLabel();
          }

          final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)o;
          String text = StringUtil.first(methodCall.getText(), 100, true).replace('\n', ' ');

          return new JBLabel(text, RECURSIVE_METHOD_ICON, SwingConstants.LEFT);
        }
      };
    }
  }
}

