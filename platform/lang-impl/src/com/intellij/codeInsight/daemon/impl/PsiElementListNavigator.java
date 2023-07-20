// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.navigation.BackgroundUpdaterTask;
import com.intellij.find.FindUtil;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GenericListComponentUpdater;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.usages.UsageView;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

public final class PsiElementListNavigator {

  private PsiElementListNavigator() {
  }

  public static <T extends NavigatablePsiElement> void openTargets(@NotNull MouseEvent e,
                                                                   T @NotNull [] targets,
                                                                   @NlsContexts.PopupTitle String title,
                                                                   @NlsContexts.TabTitle String findUsagesTitle,
                                                                   ListCellRenderer<? super T> listRenderer) {
    openTargets(e, targets, title, findUsagesTitle, listRenderer, null);
  }

  public static <T extends NavigatablePsiElement> void openTargets(@NotNull MouseEvent e,
                                                                   T @NotNull [] targets,
                                                                   @NlsContexts.PopupTitle String title,
                                                                   @NlsContexts.TabTitle String findUsagesTitle,
                                                                   ListCellRenderer<? super T> listRenderer,
                                                                   @Nullable BackgroundUpdaterTask listUpdaterTask) {
    JBPopup popup = navigateOrCreatePopup(targets, title, findUsagesTitle, listRenderer, listUpdaterTask);
    if (popup != null) {
      RelativePoint point = new RelativePoint(e);
      if (listUpdaterTask != null) {
        runActionAndListUpdaterTask(() -> popup.show(point), listUpdaterTask);
      }
      else {
        popup.show(point);
      }
    }
  }

  /**
   * @deprecated Use {@link com.intellij.codeInsight.navigation.PsiTargetNavigator }
   */
  @Deprecated
  public static <T extends NavigatablePsiElement> void openTargets(@NotNull Editor e,
                                                                   T @NotNull [] targets,
                                                                   @NlsContexts.PopupTitle String title,
                                                                   @NlsContexts.TabTitle String findUsagesTitle,
                                                                   ListCellRenderer<? super T> listRenderer) {
    openTargets(e, targets, title, findUsagesTitle, listRenderer, null);
  }

  public static <T extends NavigatablePsiElement> void openTargets(@NotNull Editor e,
                                                                   T @NotNull [] targets,
                                                                   @NlsContexts.PopupTitle String title,
                                                                   @NlsContexts.TabTitle String findUsagesTitle,
                                                                   ListCellRenderer<? super T> listRenderer,
                                                                   @Nullable BackgroundUpdaterTask listUpdaterTask) {
    JBPopup popup = navigateOrCreatePopup(targets, title, findUsagesTitle, listRenderer, listUpdaterTask);
    if (popup != null) {
      if (listUpdaterTask != null) {
        runActionAndListUpdaterTask(() -> popup.showInBestPositionFor(e), listUpdaterTask);
      }
      else {
        popup.showInBestPositionFor(e);
      }
    }
  }

  /**
   * @see #navigateOrCreatePopup(NavigatablePsiElement[], String, String, ListCellRenderer, BackgroundUpdaterTask, Consumer)
   */
  private static void runActionAndListUpdaterTask(@NotNull Runnable action, @NotNull BackgroundUpdaterTask listUpdaterTask) {
    action.run();
    ProgressManager.getInstance().run(listUpdaterTask);
  }

  public static @Nullable <T extends NavigatablePsiElement> JBPopup navigateOrCreatePopup(T @NotNull [] targets,
                                                                                          @NlsContexts.PopupTitle String title,
                                                                                          @NlsContexts.TabTitle String findUsagesTitle,
                                                                                          ListCellRenderer<? super T> listRenderer,
                                                                                          @Nullable BackgroundUpdaterTask listUpdaterTask) {
    return navigateOrCreatePopup(targets, title, findUsagesTitle, listRenderer, listUpdaterTask, selectedElements -> {
      for (NavigatablePsiElement selected : selectedElements) {
        if (selected.isValid()) {
          selected.navigate(true);
        }
      }
    });
  }

  /**
   * listUpdaterTask should be started after alarm is initialized so one-item popup won't blink
   */
  public static @Nullable <T extends NavigatablePsiElement> JBPopup navigateOrCreatePopup(T @NotNull [] targets,
                                                                                @NlsContexts.PopupTitle String title,
                                                                                @NlsContexts.TabTitle String findUsagesTitle,
                                                                                ListCellRenderer<? super T> listRenderer,
                                                                                @Nullable BackgroundUpdaterTask listUpdaterTask,
                                                                                @NotNull Consumer<? super T[]> consumer) {
    return new NavigateOrPopupHelper<>(targets, title)
      .setFindUsagesTitle(findUsagesTitle)
      .setListRenderer(listRenderer)
      .setListUpdaterTask(listUpdaterTask)
      .setTargetsConsumer(consumer)
      .navigateOrCreatePopup();
  }

  // Helper makes it easier to customize shown popup.
  public static class NavigateOrPopupHelper<T extends NavigatablePsiElement> {

    private final T @NotNull [] myTargets;

    private final @NlsContexts.PopupTitle String myTitle;

    private Consumer<? super T[]> myTargetsConsumer;

    private @Nullable @NlsContexts.TabTitle String myFindUsagesTitle;

    private @Nullable ListCellRenderer<? super T> myListRenderer;

    private @Nullable BackgroundUpdaterTask myListUpdaterTask;

    private @Nullable Project myProject;

    public NavigateOrPopupHelper(T @NotNull [] targets, @NlsContexts.PopupTitle String title) {
      myTargets = targets;
      myTitle = title;
      myTargetsConsumer = selectedElements -> {
        for (NavigatablePsiElement element : selectedElements) {
          if (element.isValid()) {
            element.navigate(true);
          }
        }
      };
    }

    public @NotNull NavigateOrPopupHelper<T> setFindUsagesTitle(@Nullable @NlsContexts.TabTitle String findUsagesTitle) {
      myFindUsagesTitle = findUsagesTitle;
      return this;
    }

    public @NotNull NavigateOrPopupHelper<T> setListRenderer(@Nullable ListCellRenderer<? super T> listRenderer) {
      myListRenderer = listRenderer;
      return this;
    }

    public @NotNull NavigateOrPopupHelper<T> setListUpdaterTask(@Nullable BackgroundUpdaterTask listUpdaterTask) {
      myListUpdaterTask = listUpdaterTask;
      return this;
    }

    public @NotNull NavigateOrPopupHelper<T> setTargetsConsumer(@NotNull Consumer<? super T[]> targetsConsumer) {
      myTargetsConsumer = targetsConsumer;
      return this;
    }

    public @NotNull NavigateOrPopupHelper<T> setProject(@Nullable Project project) {
      myProject = project;
      return this;
    }

    public final @Nullable JBPopup navigateOrCreatePopup() {
      if (myTargets.length == 0) {
        if (!allowEmptyTargets()) {
          return null; // empty initial targets are not allowed
        }
        if (myListUpdaterTask == null || myListUpdaterTask.isFinished()) {
          return null; // there will be no targets.
        }
      }
      if (myTargets.length == 1 && (myListUpdaterTask == null || myListUpdaterTask.isFinished())) {
        myTargetsConsumer.consume(myTargets);
        return null;
      }
      List<T> initialTargetsList = Arrays.asList(myTargets);
      Ref<T[]> updatedTargetsList = Ref.create(myTargets);

      IPopupChooserBuilder<T> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(initialTargetsList);
      afterPopupBuilderCreated(builder);
      if (myListRenderer instanceof PsiElementListCellRenderer<?> psiElementListCellRenderer) {
        psiElementListCellRenderer.installSpeedSearch(builder, true);
        psiElementListCellRenderer.setUsedInPopup(true);
      }

      IPopupChooserBuilder<T> popupChooserBuilder = builder.
        setTitle(myTitle).
        setMovable(true).
        setFont(EditorUtil.getEditorFont()).
        setRenderer(myListRenderer).
        withHintUpdateSupply().
        setResizable(true).
        setItemsChosenCallback(
          elements -> myTargetsConsumer.consume((T[])elements.toArray(NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY))).
        setCancelCallback(() -> {
          if (myListUpdaterTask != null) {
            myListUpdaterTask.cancelTask();
          }
          return true;
        });
      Ref<UsageView> usageView = new Ref<>();
      if (myFindUsagesTitle != null) {
        popupChooserBuilder = popupChooserBuilder.setCouldPin(popup -> {
          usageView.set(FindUtil.showInUsageView(null, updatedTargetsList.get(), myFindUsagesTitle, getProject()));
          popup.cancel();
          return false;
        });
      }

      JBPopup popup = popupChooserBuilder.createPopup();
      if (builder instanceof PopupChooserBuilder<?> castedBuilder) {
        JBList<NavigatablePsiElement> list = (JBList<NavigatablePsiElement>)castedBuilder.getChooserComponent();
        list.setTransferHandler(new TransferHandler() {
          @Override
          protected Transferable createTransferable(JComponent c) {
            Object[] selectedValues = list.getSelectedValues();
            PsiElement[] copy = new PsiElement[selectedValues.length];
            for (int i = 0; i < selectedValues.length; i++) {
              copy[i] = (PsiElement)selectedValues[i];
            }
            return PsiCopyPasteManager.newTransferable(copy);
          }

          @Override
          public int getSourceActions(JComponent c) {
            return COPY;
          }
        });

        JScrollPane pane = castedBuilder.getScrollPane();
        if (ExperimentalUI.isNewUI()) {
          list.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
        }
        else {
          pane.setBorder(null);
        }
        pane.setViewportBorder(null);
      }

      if (myListUpdaterTask != null) {
        GenericListComponentUpdater<T> popupUpdater = builder.getBackgroundUpdater();
        myListUpdaterTask.init(popup, new GenericListComponentUpdater<PsiElement>() {
          @Override
          public void replaceModel(@NotNull List<? extends PsiElement> data) {
            T[] array = (T[])data.toArray(NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY);
            updatedTargetsList.set(array);
            popupUpdater.replaceModel(Arrays.asList(array));
          }

          @Override
          public void paintBusy(boolean paintBusy) {
            popupUpdater.paintBusy(paintBusy);
          }
        }, usageView);
      }
      return popup;
    }

    private @NotNull Project getProject() {
      if (myProject != null) {
        return myProject;
      }
      assert !allowEmptyTargets() : "Project was not set and cannot be taken from targets";
      return myTargets[0].getProject();
    }

    protected boolean allowEmptyTargets() {
      return false;
    }

    protected void afterPopupBuilderCreated(@NotNull IPopupChooserBuilder<T> builder) {
      // Do nothing by default
    }
  }
}
