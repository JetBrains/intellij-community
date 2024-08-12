// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.hint.ImplementationPopupManager;
import com.intellij.codeInsight.hint.ImplementationViewElement;
import com.intellij.codeInsight.hint.ImplementationViewSession;
import com.intellij.codeInsight.hint.ImplementationViewSessionFactory;
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper;
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public abstract class ShowRelatedElementsActionBase extends DumbAwareAction implements PopupAction {

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
  public void update(final @NotNull AnActionEvent e) {
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
      DumbService.getInstance(project).showDumbModeNotificationForAction(getIndexNotReadyMessage(), ActionManager.getInstance().getId(this));
    }
  }

  protected abstract @NotNull List<ImplementationViewSessionFactory> getSessionFactories();

  protected abstract @NotNull @NlsContexts.PopupContent String getIndexNotReadyMessage();

  private void updateElementImplementations(Object lookupItemObject, ImplementationViewSession session) {
    if (lookupItemObject instanceof PsiItemWithSimilarity<?> itemWithSimilarity)  {
      lookupItemObject = itemWithSimilarity.getValue();
    }
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
    if (impls.isEmpty()) return;

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

    ImplementationPopupManager.getInstance()
      .showImplementationsPopup(session, impls, index,
                                getPopupTitle(session), couldPinPopup(),
                                invokedFromEditor, invokedByShortcut,
                                lookupItemObject -> {
                                  updateElementImplementations(lookupItemObject, session);
                                  return Unit.INSTANCE;
                                });
  }

  protected void triggerFeatureUsed(@NotNull Project project){
  }

  protected abstract @NotNull @NlsContexts.PopupTitle String getPopupTitle(@NotNull ImplementationViewSession session);

  protected abstract boolean couldPinPopup();

  protected boolean isIncludeAlwaysSelf() {
    return true;
  }

  protected boolean isSearchDeep() {
    return false;
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
}
