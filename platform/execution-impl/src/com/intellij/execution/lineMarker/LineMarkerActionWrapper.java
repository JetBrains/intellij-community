// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ExecutorGroupActionGroup;
import com.intellij.execution.actions.RunContextAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class LineMarkerActionWrapper extends ActionGroup implements PriorityAction, ActionWithDelegate<AnAction> {
  public static final Key<Pair<PsiElement, DataContext>> LOCATION_WRAPPER = Key.create("LOCATION_WRAPPER");

  protected final SmartPsiElementPointer<PsiElement> myElement;
  private final AnAction myOrigin;

  public LineMarkerActionWrapper(@NotNull PsiElement element, @NotNull AnAction origin) {
    this(SmartPointerManager.createPointer(element), origin);
  }

  private LineMarkerActionWrapper(@NotNull SmartPsiElementPointer<PsiElement> element, @NotNull AnAction origin) {
    myElement = element;
    myOrigin = origin;
    copyFrom(origin);
    if (!(myOrigin instanceof ActionGroup)) {
      getTemplatePresentation().setPerformGroup(true);
      getTemplatePresentation().setPopupGroup(true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return myOrigin.getActionUpdateThread();
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (myOrigin instanceof ExecutorAction o &&
        o.getOrigin() instanceof ExecutorGroupActionGroup oo) {
      int order = o.getOrder();
      return ContainerUtil.map2Array(
        oo.getChildren(), EMPTY_ARRAY,
        action -> new LineMarkerActionWrapper(myElement, ExecutorAction.wrap(
          action, ((RunContextAction)action).getExecutor(), order)));
    }
    if (myOrigin instanceof ActionGroup o) {
      return o.getChildren(e == null ? null : wrapEvent(e));
    }
    return EMPTY_ARRAY;
  }

  @Override
  public boolean isDumbAware() {
    return myOrigin.isDumbAware();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    AnActionEvent wrapped = wrapEvent(e);
    myOrigin.update(wrapped);
    Icon icon = wrapped.getPresentation().getIcon();
    if (icon != null) {
      e.getPresentation().setIcon(icon);
    }
  }

  private @NotNull AnActionEvent wrapEvent(@NotNull AnActionEvent e) {
    return e.withDataContext(wrapContext(e.getDataContext()));
  }

  private @NotNull DataContext wrapContext(@NotNull DataContext dataContext) {
    Pair<PsiElement, DataContext> pair = DataManager.getInstance().loadFromDataContext(dataContext, LOCATION_WRAPPER);
    PsiElement element = myElement.getElement();
    if (pair == null || pair.first != element) {
      DataContext wrapper = CustomizedDataContext.withSnapshot(dataContext, sink -> {
        sink.lazy(Location.DATA_KEY, () -> {
          PsiElement e = myElement.getElement();
          return e != null && e.isValid() ? new PsiLocation<>(e) : null;
        });
      });
      pair = Pair.pair(element, wrapper);
      DataManager.getInstance().saveInDataContext(dataContext, LOCATION_WRAPPER, pair);
    }
    return pair.second;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myOrigin.actionPerformed(wrapEvent(e));
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.TOP;
  }

  @Override
  public @NotNull AnAction getDelegate() {
    return myOrigin;
  }
}