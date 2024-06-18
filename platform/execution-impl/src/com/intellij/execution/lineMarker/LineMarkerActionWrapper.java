// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ExecutorGroupActionGroup;
import com.intellij.execution.actions.RunContextAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class LineMarkerActionWrapper extends ActionGroup
  implements DataSnapshotProvider, PriorityAction, ActionWithDelegate<AnAction> {

  @Deprecated(forRemoval = true)
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
    return ActionWrapperUtil.getActionUpdateThread(this, myOrigin);
  }

  @Override
  public @NotNull AnAction getDelegate() {
    return myOrigin;
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.TOP;
  }

  @Override
  public void dataSnapshot(@NotNull DataSink sink) {
    sink.lazy(Location.DATA_KEY, () -> {
      PsiElement e = myElement.getElement();
      return e != null && e.isValid() ? new PsiLocation<>(e) : null;
    });
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
      return ActionWrapperUtil.getChildren(e, this, o);
    }
    return EMPTY_ARRAY;
  }

  @Override
  public boolean isDumbAware() {
    return myOrigin.isDumbAware();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ActionWrapperUtil.update(e, this, myOrigin);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ActionWrapperUtil.actionPerformed(e, this, myOrigin);
  }
}