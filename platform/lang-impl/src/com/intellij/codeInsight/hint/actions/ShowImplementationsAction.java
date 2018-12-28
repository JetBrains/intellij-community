// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.hint.ImplementationViewElement;
import com.intellij.codeInsight.hint.PsiImplementationViewElement;
import com.intellij.codeInsight.hint.PsiImplementationViewSession;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.navigation.BackgroundUpdaterTask;
import com.intellij.codeInsight.navigation.ImplementationSearcher;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ListComponentUpdater;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.reference.SoftReference;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShowImplementationsAction extends AnAction implements PopupAction {
  @NonNls public static final String CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE = "codeassists.quickdefinition.lookup";
  @NonNls public static final String CODEASSISTS_QUICKDEFINITION_FEATURE = "codeassists.quickdefinition";

  private Reference<JBPopup> myPopupRef;
  private Reference<ImplementationsUpdaterTask> myTaskRef;

  public ShowImplementationsAction() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  @Override
  public boolean startInTransaction() {
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    performForContext(e.getDataContext(), true);
  }

  @TestOnly
  public void performForContext(DataContext dataContext) {
    performForContext(dataContext, true);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }


  public void performForContext(@NotNull DataContext dataContext, boolean invokedByShortcut) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    boolean isInvokedFromEditor = CommonDataKeys.EDITOR.getData(dataContext) != null;

    PsiImplementationViewSession psiImplementationViewSession = PsiImplementationViewSession.create(dataContext, project, isSearchDeep());
    if (psiImplementationViewSession == null) return;
    showImplementations(psiImplementationViewSession, isInvokedFromEditor, invokedByShortcut);
  }

  private void updateElementImplementations(final Object lookupItemObject, PsiImplementationViewSession session) {
    PsiImplementationViewSession newSession = session.createSessionForLookupElement(lookupItemObject, isSearchDeep());
    if (newSession != null) {
      showImplementations(newSession, false, false);
    }
  }

  protected void showImplementations(@NotNull PsiImplementationViewSession session,
                                     boolean invokedFromEditor,
                                     boolean invokedByShortcut) {

    List<ImplementationViewElement> impls = session.getImplementationElements();
    if (impls.size() == 0) return;
    Project project = session.getProject();

    FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKDEFINITION_FEATURE);
    if (LookupManager.getInstance(project).getActiveLookup() != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE);
    }

    PsiFile file = session.getFile();
    int index = 0;
    if (invokedFromEditor && file != null && impls.size() > 1) {
      final VirtualFile virtualFile = file.getVirtualFile();
      final PsiFile containingFile = impls.get(0).getContainingFile();
      if (virtualFile != null && containingFile != null && virtualFile.equals(containingFile.getVirtualFile())) {
        final PsiFile secondContainingFile = impls.get(1).getContainingFile();
        if (secondContainingFile != containingFile) {
          index = 1;
        }
      }
    }

    final Ref<UsageView> usageView = new Ref<>();
    final String title = CodeInsightBundle.message("implementation.view.title", session.getText());
    JBPopup popup = SoftReference.dereference(myPopupRef);
    if (popup != null && popup.isVisible() && popup instanceof AbstractPopup) {
      final ImplementationViewComponent component = (ImplementationViewComponent) ((AbstractPopup)popup).getComponent();
      ((AbstractPopup)popup).setCaption(title);
      component.update(impls, index);
      updateInBackground(session, component, title, (AbstractPopup)popup, usageView);
      if (invokedByShortcut) {
        ((AbstractPopup)popup).focusPreferredComponent();
      }
      return;
    }

    final ImplementationViewComponent component = new ImplementationViewComponent(impls, index);
    if (component.hasElementsToShow()) {
      final PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(project) {
        @Override
        public void updatePopup(Object lookupItemObject) {
          updateElementImplementations(lookupItemObject, session);
        }
      };

      popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getPreferredFocusableComponent())
        .setProject(project)
        .addListener(updateProcessor)
        .addUserData(updateProcessor)
        .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(invokedFromEditor && LookupManager.getActiveLookup(session.getEditor()) == null)
        .setTitle(title)
        .setCouldPin(popup1 -> {
          usageView.set(component.showInUsageView());
          popup1.cancel();
          myTaskRef = null;
          return false;
        })
        .setCancelCallback(() -> {
          ImplementationsUpdaterTask task = SoftReference.dereference(myTaskRef);
          if (task != null) {
            task.cancelTask();
          }
          return Boolean.TRUE;
        })
        .createPopup();

      updateInBackground(session, component, title, (AbstractPopup)popup, usageView);

      PopupPositionManager.positionPopupInBestPosition(popup, session.getEditor(), DataManager.getInstance().getDataContext());
      component.setHint(popup, title);

      myPopupRef = new WeakReference<>(popup);
    }
  }

  private void updateInBackground(@NotNull PsiImplementationViewSession session,
                                  @NotNull ImplementationViewComponent component,
                                  String title,
                                  @NotNull AbstractPopup popup,
                                  @NotNull Ref<UsageView> usageView) {
    final ImplementationsUpdaterTask updaterTask = SoftReference.dereference(myTaskRef);
    if (updaterTask != null) {
      updaterTask.cancelTask();
    }

    if (!session.needUpdateInBackground()) return;  // already found
    final ImplementationsUpdaterTask task = new ImplementationsUpdaterTask(session, title, isIncludeAlwaysSelf(), component);
    task.init(popup, new ImplementationViewComponentUpdater(component, session.elementRequiresIncludeSelf() ? 1 : 0), usageView);

    myTaskRef = new WeakReference<>(task);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
  }

  protected boolean isIncludeAlwaysSelf() {
    return true;
  }

  protected boolean isSearchDeep() {
    return false;
  }

  private static class ImplementationViewComponentUpdater implements ListComponentUpdater {
    private final ImplementationViewComponent myComponent;
    private int myIncludeSelfIdx;

    ImplementationViewComponentUpdater(ImplementationViewComponent component, int includeSelfIdx) {
      myComponent = component;
      myIncludeSelfIdx = includeSelfIdx;
    }

    @Override
    public void paintBusy(boolean paintBusy) {
      //todo notify busy
    }

    @Override
    public void replaceModel(@NotNull List<? extends PsiElement> data) {
      final ImplementationViewElement[] elements = myComponent.getElements();
      final int includeSelfIdx = myIncludeSelfIdx;
      final int startIdx = elements.length - includeSelfIdx;
      List<ImplementationViewElement> result = new ArrayList<>();
      Collections.addAll(result, elements);
      for (PsiElement element : data.subList(startIdx, data.size())) {
        result.add(new PsiImplementationViewElement(element));
      }
      myComponent.update(result, myComponent.getIndex());
    }
  }

  private class ImplementationsUpdaterTask extends BackgroundUpdaterTask {
    private final String myCaption;
    private final PsiImplementationViewSession mySession;
    private final boolean myIncludeSelf;
    private final ImplementationViewComponent myComponent;
    private List<ImplementationViewElement> myElements;

    private ImplementationsUpdaterTask(PsiImplementationViewSession session,
                                       final String caption,
                                       boolean includeSelf,
                                       ImplementationViewComponent component) {
      super(session.getProject(), ImplementationSearcher.SEARCHING_FOR_IMPLEMENTATIONS, null);
      myCaption = caption;
      mySession = session;
      myIncludeSelf = includeSelf;
      myComponent = component;
    }

    @Override
    public String getCaption(int size) {
      return myCaption;
    }


    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      myElements = mySession.searchImplementationsInBackground(indicator, isSearchDeep(), myIncludeSelf, this::updateComponent);
    }

    @Override
    public int getCurrentSize() {
      if (myElements != null) return myElements.size();
      return super.getCurrentSize();
    }

    @Override
    public void onSuccess() {
      if (!cancelTask()) {
        myComponent.update(myElements, myComponent.getIndex());
      }
      super.onSuccess();
    }
  }
}
