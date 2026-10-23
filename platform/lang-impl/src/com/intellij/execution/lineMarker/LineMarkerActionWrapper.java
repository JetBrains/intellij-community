// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.ActionWrapperUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DataSnapshotProvider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

  @ApiStatus.Internal
  public LineMarkerActionWrapper(@NotNull SmartPsiElementPointer<PsiElement> element, @NotNull AnAction origin) {
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
    RunLineMarkerActionCustomizer.customizeDataSnapshotForAction(sink, myElement);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    List<@NotNull AnAction> wrappedChildren = RunLineMarkerActionCustomizer.wrapLineMarkerActionChildren(myOrigin, myElement);
    if (wrappedChildren != null) {
      return wrappedChildren.toArray(EMPTY_ARRAY);
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