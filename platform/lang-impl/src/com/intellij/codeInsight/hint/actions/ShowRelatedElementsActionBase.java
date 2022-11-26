// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.hint.ImplementationViewElement;
import com.intellij.codeInsight.hint.ImplementationViewSession;
import com.intellij.codeInsight.hint.ImplementationViewSessionFactory;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.navigation.BackgroundUpdaterTaskBase;
import com.intellij.codeInsight.navigation.ImplementationSearcher;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PopupAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GenericListComponentUpdater;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class ShowRelatedElementsActionBase extends DumbAwareAction implements PopupAction {
  private Reference<JBPopup> myPopupRef;
  private Reference<ImplementationsUpdaterTask> myTaskRef;

  public ShowRelatedElementsActionBase() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
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

    try {
      for (ImplementationViewSessionFactory factory : getSessionFactories()) {
        ImplementationViewSession session = factory.createSession(dataContext, project, isSearchDeep(), isIncludeAlwaysSelf());
        if (session != null) {
          ensureValid(session, dataContext);
          showImplementations(session, isInvokedFromEditor, invokedByShortcut);
        }
      }
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotification(getIndexNotReadyMessage());
    }
  }

  @NotNull
  protected abstract List<ImplementationViewSessionFactory> getSessionFactories();

  @NotNull
  protected abstract @NlsContexts.PopupContent String getIndexNotReadyMessage();

  private void updateElementImplementations(Object lookupItemObject, ImplementationViewSession session) {
    if (lookupItemObject instanceof PSIPresentationBgRendererWrapper.PsiItemWithPresentation) {
      lookupItemObject = ((PSIPresentationBgRendererWrapper.PsiItemWithPresentation)lookupItemObject).getItem();
    }
    ImplementationViewSessionFactory currentFactory = session.getFactory();
    ImplementationViewSession newSession = createNewSession(currentFactory, session, lookupItemObject);
    if (newSession == null) {
      for (ImplementationViewSessionFactory factory : getSessionFactories()) {
        if (currentFactory == factory) continue;
        newSession = createNewSession(factory, session, lookupItemObject);
        if (newSession != null) break;
      }
    }
    if (newSession != null) {
      ensureValid(newSession, lookupItemObject);
      Disposer.dispose(session);
      showImplementations(newSession, false, false);
    }
  }

  private ImplementationViewSession createNewSession(ImplementationViewSessionFactory factory,
                                                     ImplementationViewSession session,
                                                     Object lookupItemObject) {
    ensureValid(session, lookupItemObject);
    return factory.createSessionForLookupElement(session.getProject(), session.getEditor(), session.getFile(), lookupItemObject,
                                                 isSearchDeep(), isIncludeAlwaysSelf());
  }

  protected void showImplementations(@NotNull ImplementationViewSession session,
                                     boolean invokedFromEditor,
                                     boolean invokedByShortcut) {

    List<ImplementationViewElement> impls = session.getImplementationElements();
    if (impls.size() == 0) return;
    Project project = session.getProject();
    triggerFeatureUsed(project);
    VirtualFile virtualFile = session.getFile();
    int index = 0;
    if (invokedFromEditor && virtualFile != null && impls.size() > 1) {
      final VirtualFile containingFile = impls.get(0).getContainingFile();
      if (virtualFile.equals(containingFile)) {
        final VirtualFile secondContainingFile = impls.get(1).getContainingFile();
        if (secondContainingFile != null && !secondContainingFile.equals(containingFile)) {
          index = 1;
        }
      }
    }

    final Ref<UsageView> usageView = new Ref<>();
    final String title = getPopupTitle(session);
    JBPopup popup = SoftReference.dereference(myPopupRef);
    if (popup != null && popup.isVisible() && popup instanceof AbstractPopup) {
      final ImplementationViewComponent component = (ImplementationViewComponent)((AbstractPopup)popup).getComponent();
      if (component != null) {
        component.update(impls, index);
        updateInBackground(session, component, (AbstractPopup)popup, usageView);
        if (invokedByShortcut) {
          ((AbstractPopup)popup).focusPreferredComponent();
        }
        return;
      }
    }

    Consumer<ImplementationViewComponent> processor = couldPinPopup() ? component -> {
      usageView.set(component.showInUsageView());
      myTaskRef = null; } : null;

    final ImplementationViewComponent component = new ImplementationViewComponent(impls, index, processor);
    if (component.hasElementsToShow()) {
      final PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(project) {
        @Override
        public void updatePopup(Object lookupItemObject) {
          updateElementImplementations(lookupItemObject, session);
        }
      };

      ComponentPopupBuilder popupBuilder = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(component, component.getPreferredFocusableComponent())
        .setProject(project)
        .addListener(updateProcessor)
        .addUserData(updateProcessor)
        .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(invokedFromEditor && LookupManager.getActiveLookup(session.getEditor()) == null)
        .setCancelCallback(() -> {
          ImplementationsUpdaterTask task = SoftReference.dereference(myTaskRef);
          if (task != null) {
            task.cancelTask();
          }
          Disposer.dispose(session);
          return Boolean.TRUE;
        });
      WindowMoveListener listener = new WindowMoveListener();
      listener.installTo(component);
      Disposer.register(session, () -> listener.uninstallFrom(component));
      popup = popupBuilder.createPopup();

      updateInBackground(session, component, (AbstractPopup)popup, usageView);

      PopupPositionManager.positionPopupInBestPosition(popup, session.getEditor(), DataManager.getInstance().getDataContext());
      component.setHint(popup, title);

      myPopupRef = new WeakReference<>(popup);
    }
  }

  protected void triggerFeatureUsed(@NotNull Project project){
  }

  @NotNull
  protected abstract @NlsContexts.PopupTitle String getPopupTitle(@NotNull ImplementationViewSession session);

  protected abstract boolean couldPinPopup();

  private void updateInBackground(@NotNull ImplementationViewSession session,
                                  @NotNull ImplementationViewComponent component,
                                  @NotNull AbstractPopup popup,
                                  @NotNull Ref<? extends UsageView> usageView) {
    final ImplementationsUpdaterTask updaterTask = SoftReference.dereference(myTaskRef);
    if (updaterTask != null) {
      updaterTask.cancelTask();
    }

    if (!session.needUpdateInBackground()) return;  // already found
    final ImplementationsUpdaterTask task = new ImplementationsUpdaterTask(session, component);
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

  private static class ImplementationViewComponentUpdater implements GenericListComponentUpdater<ImplementationViewElement> {
    private final ImplementationViewComponent myComponent;
    private final int myIncludeSelfIdx;

    ImplementationViewComponentUpdater(ImplementationViewComponent component, int includeSelfIdx) {
      myComponent = component;
      myIncludeSelfIdx = includeSelfIdx;
    }

    @Override
    public void paintBusy(boolean paintBusy) {
      //todo notify busy
    }

    @Override
    public void replaceModel(@NotNull List<? extends ImplementationViewElement> data) {
      final ImplementationViewElement[] elements = myComponent.getElements();
      final int startIdx = elements.length - myIncludeSelfIdx;
      List<ImplementationViewElement> result = new ArrayList<>();
      Collections.addAll(result, elements);
      result.addAll(data.subList(startIdx, data.size()));
      myComponent.update(result, myComponent.getIndex());
    }
  }

  // See: https://web.ea.pages.jetbrains.team/#/issue/660785
  private static void ensureValid(@NotNull ImplementationViewSession session, @Nullable Object context) {
    PsiFile contextFile = null;
    if (context instanceof DataContext dataContext) {
      contextFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    }
    if (context instanceof PsiElement psiElement) {
      contextFile = psiElement.getContainingFile();
    }
    VirtualFile contextVirtualFile = contextFile != null ? contextFile.getVirtualFile() : null;
    VirtualFile sessionVirtualFile = session.getFile();
    if (contextVirtualFile != null && contextVirtualFile.equals(sessionVirtualFile)) {
      PsiUtilCore.ensureValid(contextFile);
    }
    else if (sessionVirtualFile != null && !sessionVirtualFile.isValid()) {
      throw new InvalidVirtualFileAccessException(sessionVirtualFile);
    }
  }

  private static final class ImplementationsUpdaterTask extends BackgroundUpdaterTaskBase<ImplementationViewElement> {
    private final ImplementationViewSession mySession;
    private final ImplementationViewComponent myComponent;
    private List<ImplementationViewElement> myElements;

    private ImplementationsUpdaterTask(ImplementationViewSession session,
                                       ImplementationViewComponent component) {
      super(session.getProject(), ImplementationSearcher.getSearchingForImplementations(), null);
      mySession = session;
      myComponent = component;
    }

    @Override
    public String getCaption(int size) {
      return null;
    }

    @Override
    protected Usage createUsage(ImplementationViewElement element) {
      return element.getUsage();
    }


    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      myElements = mySession.searchImplementationsInBackground(indicator, this::updateComponent);
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
