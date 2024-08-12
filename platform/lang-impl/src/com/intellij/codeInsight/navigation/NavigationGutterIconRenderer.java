// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.runInReadActionWithWriteActionPriority;

public abstract class NavigationGutterIconRenderer extends GutterIconRenderer
  implements GutterIconNavigationHandler<PsiElement>, DumbAware {
  private static final Logger LOG = Logger.getInstance(NavigationGutterIconRenderer.class);
  protected final @PopupTitle String myPopupTitle;
  private final @PopupContent String myEmptyText;
  protected final Computable<? extends PsiElementListCellRenderer> myCellRenderer;
  private Supplier<? extends PsiTargetPresentationRenderer<PsiElement>> myTargetRenderer;
  private Project myProject;
  private final NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> myPointers;
  private final boolean myComputeTargetsInBackground;

  private final @Nullable GutterIconNavigationHandler<? super PsiElement> myNavigationHandler;

  protected NavigationGutterIconRenderer(@PopupTitle String popupTitle,
                                         @PopupContent String emptyText,
                                         @NotNull Computable<? extends PsiElementListCellRenderer<?>> cellRenderer,
                                         @NotNull NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> pointers) {
    this(popupTitle, emptyText, cellRenderer, pointers, true);
  }

  protected NavigationGutterIconRenderer(@PopupTitle String popupTitle,
                                         @PopupContent String emptyText,
                                         @NotNull Computable<? extends PsiElementListCellRenderer<?>> cellRenderer,
                                         @NotNull NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> pointers,
                                         boolean computeTargetsInBackground) {
    this(popupTitle, emptyText, cellRenderer, pointers, computeTargetsInBackground, null);
  }

  protected NavigationGutterIconRenderer(@PopupTitle String popupTitle,
                                         @PopupContent String emptyText,
                                         @NotNull Computable<? extends PsiElementListCellRenderer<?>> cellRenderer,
                                         @NotNull NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> pointers,
                                         boolean computeTargetsInBackground,
                                         @Nullable GutterIconNavigationHandler<? super PsiElement> navigationHandler) {
    myPopupTitle = popupTitle;
    myEmptyText = emptyText;
    myCellRenderer = cellRenderer;
    myPointers = pointers;
    myComputeTargetsInBackground = computeTargetsInBackground;
    myNavigationHandler = navigationHandler;
  }

  public void setTargetRenderer(Supplier<? extends PsiTargetPresentationRenderer<PsiElement>> targetRenderer) {
    myTargetRenderer = targetRenderer;
  }

  public void setProject(Project project) {
    myProject = project;
  }

  @Override
  public boolean isNavigateAction() {
    return true;
  }

  public @NotNull List<PsiElement> getTargetElements() {
    List<SmartPsiElementPointer<?>> pointers = myPointers.getValue();
    if (pointers.isEmpty()) return Collections.emptyList();
    Project project = pointers.get(0).getProject();
    DumbService dumbService = DumbService.getInstance(project);
    return dumbService.computeWithAlternativeResolveEnabled(() -> ContainerUtil.mapNotNull(pointers, smartPsiElementPointer -> smartPsiElementPointer.getElement()));
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final NavigationGutterIconRenderer renderer = (NavigationGutterIconRenderer)o;

    if (myEmptyText != null ? !myEmptyText.equals(renderer.myEmptyText) : renderer.myEmptyText != null) return false;
    if (!myPointers.getValue().equals(renderer.myPointers.getValue())) return false;
    if (myPopupTitle != null ? !myPopupTitle.equals(renderer.myPopupTitle) : renderer.myPopupTitle != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myPopupTitle != null ? myPopupTitle.hashCode() : 0;
    result = 31 * result + (myEmptyText != null ? myEmptyText.hashCode() : 0);
    result = 31 * result + myPointers.getValue().hashCode();
    return result;
  }

  @Override
  public @Nullable AnAction getClickAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        navigate((MouseEvent)e.getInputEvent(), null);
      }
    };
  }

  @Override
  public void navigate(@Nullable MouseEvent event, @Nullable PsiElement elt) {
    if (event != null && myComputeTargetsInBackground && event.getComponent() != null) {
      navigateTargetsAsync(event);
    }
    else {
      navigateTargets(event, getTargetElements());
    }
  }

  private void navigateTargetsAsync(@NotNull MouseEvent event) {
    Component component = event.getComponent();
    Runnable loadingRemover = component instanceof EditorGutterComponentEx ?
                              ((EditorGutterComponentEx)component).setLoadingIconForCurrentGutterMark() : null;
    AppExecutorUtil.getAppExecutorService().execute(ClientId.decorateRunnable(() -> {

      ProgressManager.getInstance().computePrioritized(() -> {
        ProgressManager.getInstance().executeProcessUnderProgress(() -> {
          Ref<List<PsiElement>> targets = Ref.create();
          boolean success = runInReadActionWithWriteActionPriority(() -> targets.set(getTargetElements()));

          ApplicationManager.getApplication().invokeLater(() -> {
            if (loadingRemover != null) {
              loadingRemover.run();
            }
            if (success) {
              navigateTargets(event, targets.get());
            }
          });
        }, new EmptyProgressIndicator());
        return null;
      });
    }));
  }

  private void navigateTargets(@Nullable MouseEvent event, @NotNull List<? extends PsiElement> targets) {
    if (targets.isEmpty()) {
      if (myEmptyText != null) {
        if (event != null) {
          JComponent label = HintUtil.createErrorLabel(myEmptyText);
          label.setBorder(JBUI.Borders.empty(2, 7));
          JBPopupFactory.getInstance().createBalloonBuilder(label)
            .setFadeoutTime(3000)
            .setFillColor(HintUtil.getErrorColor())
            .createBalloon()
            .show(new RelativePoint(event), Balloon.Position.above);
        }
      }
    }
    else {
      navigateToItems(event);
    }
  }

  protected void navigateToItems(@Nullable MouseEvent event) {
    List<Pair<PsiElement, Navigatable>> navigatables = new ArrayList<>();
    for (SmartPsiElementPointer<?> pointer : myPointers.getValue()) {
      ContainerUtil.addIfNotNull(navigatables, getNavigatable(pointer));
    }
    if (navigatables.size() == 1) {
      if (myNavigationHandler != null) {
        myNavigationHandler.navigate(event, navigatables.get(0).first);
      } else {
        navigatables.get(0).second.navigate(true);
      }
    }
    else if (event != null) {
      //noinspection unchecked
      PsiElementListCellRenderer<PsiElement> renderer = (PsiElementListCellRenderer<PsiElement>)myCellRenderer.compute();
      if (myTargetRenderer != null || DefaultPsiElementCellRenderer.class == renderer.getClass()) {
        PsiTargetNavigator<PsiElement> navigator = new PsiTargetNavigator<>(() -> getTargetElements());
        if (myTargetRenderer != null) {
          navigator.presentationProvider(myTargetRenderer.get());
        }
        navigator.navigate(new RelativePoint(event), myPopupTitle, myProject, element -> getElementProcessor(event).execute(element));
        return;
      }
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error("Do not use PsiElementListCellRenderer: " + myCellRenderer + ". Use PsiTargetPresentationRenderer via NavigationGutterIconBuilder.setTargetRenderer()");
      }
      PsiElement[] elements = PsiUtilCore.toPsiElementArray(getTargetElements());
      JBPopup popup = NavigationUtil.getPsiElementPopup(elements, renderer, myPopupTitle, getElementProcessor(event));
      popup.show(new RelativePoint(event));
    }
  }

  private @NotNull PsiElementProcessor<PsiElement> getElementProcessor(@NotNull MouseEvent event) {
    return element -> {
      if (myNavigationHandler != null) {
        myNavigationHandler.navigate(event, element);
      }
      else {
        Navigatable descriptor = EditSourceUtil.getDescriptor(element);
        if (descriptor != null && descriptor.canNavigate()) {
          descriptor.navigate(true);
        }
      }
      return true;
    };
  }

  private static @Nullable Pair<PsiElement, Navigatable> getNavigatable(SmartPsiElementPointer<?> pointer) {
    Navigatable element = getNavigationElement(pointer);
    if (element != null) return new Pair<>(pointer.getElement(), element);

    VirtualFile virtualFile = pointer.getVirtualFile();
    Segment actualRange = pointer.getRange();
    if (virtualFile != null && actualRange != null && virtualFile.isValid() && actualRange.getStartOffset() >= 0) {
      return new Pair<>(pointer.getElement(), new OpenFileDescriptor(pointer.getProject(), virtualFile, actualRange.getStartOffset()));
    }

    return null;
  }

  private static @Nullable Navigatable getNavigationElement(SmartPsiElementPointer<?> pointer) {
    PsiElement element = pointer.getElement();
    if (element == null) return null;
    final PsiElement navigationElement = element.getNavigationElement();
    return navigationElement instanceof Navigatable ? (Navigatable)navigationElement : null;
  }
}
