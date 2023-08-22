// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.execution.ExecutorRegistryImpl;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.RunContextAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 */
public class LineMarkerActionWrapper extends ActionGroup implements PriorityAction, ActionWithDelegate<AnAction> {
  private static final Logger LOG = Logger.getInstance(LineMarkerActionWrapper.class);
  public static final Key<Pair<PsiElement, MyDataContext>> LOCATION_WRAPPER = Key.create("LOCATION_WRAPPER");

  protected final SmartPsiElementPointer<PsiElement> myElement;
  private final AnAction myOrigin;

  public LineMarkerActionWrapper(PsiElement element, @NotNull AnAction origin) {
    myElement = SmartPointerManager.createPointer(element);
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
    // This is quickfix for IDEA-208231
    // See com.intellij.codeInsight.daemon.impl.GutterIntentionMenuContributor.addActions(AnAction, List<? super IntentionActionDescriptor>, GutterIconRenderer, AtomicInteger, DataContext)`
    if (myOrigin instanceof ExecutorAction) {
      if (((ExecutorAction)myOrigin).getOrigin() instanceof ExecutorRegistryImpl.ExecutorGroupActionGroup) {
        final AnAction[] children =
          ((ExecutorRegistryImpl.ExecutorGroupActionGroup)((ExecutorAction)myOrigin).getOrigin()).getChildren(null);
        LOG.assertTrue(ContainerUtil.all(Arrays.asList(children), o -> o instanceof RunContextAction));
        return ContainerUtil.mapNotNull(children, o -> {
          PsiElement element = myElement.getElement();
          return element != null ? new LineMarkerActionWrapper(element, ExecutorAction.wrap((RunContextAction)o, ((ExecutorAction)myOrigin).getOrder())) : null;
        }).toArray(AnAction.EMPTY_ARRAY);
      }
    }
    if (myOrigin instanceof ActionGroup) {
      return ((ActionGroup)myOrigin).getChildren(e == null ? null : wrapEvent(e));
    }
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public boolean isDumbAware() {
    return myOrigin.isDumbAware();
  }

  @Override
  public boolean isPopup() {
    return !(myOrigin instanceof ActionGroup) || ((ActionGroup)myOrigin).isPopup();
  }

  @Override
  public boolean hideIfNoVisibleChildren() {
    return myOrigin instanceof ActionGroup && ((ActionGroup)myOrigin).hideIfNoVisibleChildren();
  }

  @Override
  public boolean disableIfNoVisibleChildren() {
    return myOrigin instanceof ActionGroup && ((ActionGroup)myOrigin).disableIfNoVisibleChildren();
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

  @NotNull
  private AnActionEvent wrapEvent(@NotNull AnActionEvent e) {
    return e.withDataContext(wrapContext(e.getDataContext()));
  }

  @NotNull
  private DataContext wrapContext(DataContext dataContext) {
    Pair<PsiElement, MyDataContext> pair = DataManager.getInstance().loadFromDataContext(dataContext, LOCATION_WRAPPER);
    PsiElement element = myElement.getElement();
    if (pair == null || pair.first != element) {
      pair = Pair.pair(element, new MyDataContext(dataContext));
      DataManager.getInstance().saveInDataContext(dataContext, LOCATION_WRAPPER, pair);
    }
    return pair.second;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myOrigin.actionPerformed(wrapEvent(e));
  }

  @NotNull
  @Override
  public Priority getPriority() {
    return Priority.TOP;
  }

  @NotNull
  @Override
  public AnAction getDelegate() {
    return myOrigin;
  }

  private class MyDataContext extends DataContextWrapper {

    MyDataContext(DataContext delegate) {
      super(delegate);
    }

    @Override
    public @Nullable Object getRawCustomData(@NotNull String dataId) {
      if (Location.DATA_KEY.is(dataId)) {
        PsiElement element = myElement.getElement();
        return element != null && element.isValid() ? new PsiLocation<>(element) : null;
      }
      return null;
    }
  }
}