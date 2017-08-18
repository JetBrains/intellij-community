/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.navigation.BackgroundUpdaterTask;
import com.intellij.codeInsight.navigation.ImplementationSearcher;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

public class ShowImplementationsAction extends AnAction implements PopupAction {
  @NonNls public static final String CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE = "codeassists.quickdefinition.lookup";
  @NonNls public static final String CODEASSISTS_QUICKDEFINITION_FEATURE = "codeassists.quickdefinition";

  private static final Logger LOG = Logger.getInstance(ShowImplementationsAction.class);

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
  public void actionPerformed(AnActionEvent e) {
    performForContext(e.getDataContext(), true);
  }

  @TestOnly
  public void performForContext(DataContext dataContext) {
    performForContext(dataContext, true);
  }

  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext);

    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    element = getElement(project, file, editor, element);

    PsiFile containingFile = element != null ? element.getContainingFile() : file;
    boolean enabled = !(containingFile == null || !containingFile.getViewProvider().isPhysical());
    e.getPresentation().setEnabled(enabled);
  }


  protected static Editor getEditor(@NotNull DataContext dataContext) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    if (editor == null) {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      if (file != null) {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          final FileEditor fileEditor = FileEditorManager.getInstance(file.getProject()).getSelectedEditor(virtualFile);
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }
      }
    }
    return editor;
  }

  public void performForContext(@NotNull DataContext dataContext, boolean invokedByShortcut) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    Editor editor = getEditor(dataContext);

    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    boolean isInvokedFromEditor = CommonDataKeys.EDITOR.getData(dataContext) != null;
    element = getElement(project, file, editor, element);

    if (element == null && file == null) return;
    PsiFile containingFile = element != null ? element.getContainingFile() : file;
    if (containingFile == null || !containingFile.getViewProvider().isPhysical()) return;


    PsiReference ref = null;
    if (editor != null) {
      ref = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
      if (element == null && ref != null) {
        element = TargetElementUtil.getInstance().adjustReference(ref);
      }
    }

    //check attached sources if any
    if (element instanceof PsiCompiledElement) {
      element = element.getNavigationElement();
    }

    String text = "";
    PsiElement[] impls = PsiElement.EMPTY_ARRAY;
    if (element != null) {
      impls = getSelfAndImplementations(editor, element, createImplementationsSearcher());
      text = SymbolPresentationUtil.getSymbolPresentableText(element);
    }

    if (impls.length == 0 && ref instanceof PsiPolyVariantReference) {
      final PsiPolyVariantReference polyReference = (PsiPolyVariantReference)ref;
      PsiElement refElement = polyReference.getElement();
      TextRange rangeInElement = polyReference.getRangeInElement();
      String refElementText = refElement.getText();
      LOG.assertTrue(rangeInElement.getEndOffset() <= refElementText.length(),
                     "Ref:" + polyReference + "; refElement: " + refElement + "; refText:" + refElementText);
      text = rangeInElement.substring(refElementText);
      final ResolveResult[] results = polyReference.multiResolve(false);
      final List<PsiElement> implsList = new ArrayList<>(results.length);

      for (ResolveResult result : results) {
        final PsiElement resolvedElement = result.getElement();

        if (resolvedElement != null && resolvedElement.isPhysical()) {
          implsList.add(resolvedElement);
        }
      }

      if (!implsList.isEmpty()) {
        impls = implsList.toArray(new PsiElement[implsList.size()]);
      }
    }


    showImplementations(impls, project, text, editor, file, element, isInvokedFromEditor, invokedByShortcut);
  }

  protected static PsiElement getElement(@NotNull Project project, PsiFile file, Editor editor, PsiElement element) {
    if (element == null && editor != null) {
      element = TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getAllAccepted());
      final PsiElement adjustedElement =
        TargetElementUtil.getInstance().adjustElement(editor, TargetElementUtil.getInstance().getAllAccepted(), element, null);
      if (adjustedElement != null) {
        element = adjustedElement;
      }
      else if (file != null) {
        element = DocumentationManager.getInstance(project).getElementFromLookup(editor, file);
      }
    }
    return element;
  }

  @NotNull
  ImplementationSearcher createImplementationsSearcher() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new ImplementationSearcher() {
        @Override
        protected PsiElement[] filterElements(PsiElement element, PsiElement[] targetElements) {
          return ShowImplementationsAction.filterElements(targetElements);
        }
      };
    }
    return new ImplementationSearcher.FirstImplementationsSearcher() {
      @Override
      protected PsiElement[] filterElements(PsiElement element, PsiElement[] targetElements) {
        return ShowImplementationsAction.filterElements(targetElements);
      }

      @Override
      protected boolean isSearchDeep() {
        return ShowImplementationsAction.this.isSearchDeep();
      }
    };
  }

  private void updateElementImplementations(final PsiElement element, final Editor editor, @NotNull Project project, final PsiFile file) {
    PsiElement[] impls = {};
    String text = "";
    if (element != null) {
     // if (element instanceof PsiPackage) return;
      PsiFile containingFile = element.getContainingFile();
      if (containingFile == null || !containingFile.getViewProvider().isPhysical()) return;

      impls = getSelfAndImplementations(editor, element, createImplementationsSearcher());
      text = SymbolPresentationUtil.getSymbolPresentableText(element);
    }

    showImplementations(impls, project, text, editor, file, element, false, false);
  }

  protected void showImplementations(@NotNull PsiElement[] impls,
                                     @NotNull final Project project,
                                     final String text,
                                     final Editor editor,
                                     final PsiFile file,
                                     final PsiElement element,
                                     boolean invokedFromEditor,
                                     boolean invokedByShortcut) {
    if (impls.length == 0) return;

    FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKDEFINITION_FEATURE);
    if (LookupManager.getInstance(project).getActiveLookup() != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE);
    }

    int index = 0;
    if (invokedFromEditor && file != null && impls.length > 1) {
      final VirtualFile virtualFile = file.getVirtualFile();
      final PsiFile containingFile = impls[0].getContainingFile();
      if (virtualFile != null && containingFile != null && virtualFile.equals(containingFile.getVirtualFile())) {
        final PsiFile secondContainingFile = impls[1].getContainingFile();
        if (secondContainingFile != containingFile) {
          index = 1;
        }
      }
    }

    final Ref<UsageView> usageView = new Ref<>();
    final String title = CodeInsightBundle.message("implementation.view.title", text);
    JBPopup popup = SoftReference.dereference(myPopupRef);
    if (popup != null && popup.isVisible() && popup instanceof AbstractPopup) {
      final ImplementationViewComponent component = (ImplementationViewComponent) ((AbstractPopup)popup).getComponent();
      ((AbstractPopup)popup).setCaption(title);
      component.update(impls, index);
      updateInBackground(editor, element, component, title, (AbstractPopup)popup, usageView);
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
          final PsiElement element = lookupItemObject instanceof PsiElement ? (PsiElement)lookupItemObject : DocumentationManager.getInstance(project).getElementFromLookup(editor, file);
          updateElementImplementations(element, editor, project, file);
        }
      };

      popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getPreferredFocusableComponent())
        .setProject(project)
        .addListener(updateProcessor)
        .addUserData(updateProcessor)
        .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(invokedFromEditor && LookupManager.getActiveLookup(editor) == null)
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

      updateInBackground(editor, element, component, title, (AbstractPopup)popup, usageView);

      PopupPositionManager.positionPopupInBestPosition(popup, editor, DataManager.getInstance().getDataContext());
      component.setHint(popup, title);

      myPopupRef = new WeakReference<>(popup);
    }
  }

  private void updateInBackground(Editor editor,
                                  @Nullable PsiElement element,
                                  @NotNull ImplementationViewComponent component,
                                  String title,
                                  @NotNull AbstractPopup popup,
                                  @NotNull Ref<UsageView> usageView) {
    final ImplementationsUpdaterTask updaterTask = SoftReference.dereference(myTaskRef);
    if (updaterTask != null) {
      updaterTask.cancelTask();
    }

    if (element == null) return; //already found
    final ImplementationsUpdaterTask task = new ImplementationsUpdaterTask(element, editor, title, isIncludeAlwaysSelf());
    task.init(popup, component, usageView);

    myTaskRef = new WeakReference<>(task);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
  }

  protected boolean isIncludeAlwaysSelf() {
    return true;
  }

  @NotNull
  private static PsiElement[] getSelfAndImplementations(Editor editor,
                                                        @NotNull PsiElement element,
                                                        @NotNull ImplementationSearcher handler) {
    return getSelfAndImplementations(editor, element, handler, !(element instanceof PomTargetPsiElement));
  }

  @NotNull
  static PsiElement[] getSelfAndImplementations(Editor editor,
                                                @NotNull PsiElement element,
                                                @NotNull ImplementationSearcher handler,
                                                final boolean includeSelfAlways) {
    final PsiElement[] handlerImplementations = handler.searchImplementations(element, editor, includeSelfAlways, true);
    if (handlerImplementations.length > 0) return handlerImplementations;

    return ReadAction.compute(() -> {
      PsiElement psiElement = element;
      PsiFile psiFile = psiElement.getContainingFile();
      if (psiFile == null) {
        // Magically, it's null for ant property declarations.
        psiElement = psiElement.getNavigationElement();
        psiFile = psiElement.getContainingFile();
        if (psiFile == null) {
          return PsiElement.EMPTY_ARRAY;
        }
      }
      if (psiFile.getVirtualFile() != null && (psiElement.getTextRange() != null || psiElement instanceof PsiFile)) {
        return new PsiElement[]{psiElement};
      }
      return PsiElement.EMPTY_ARRAY;
    });
  }

  @NotNull
  private static PsiElement[] filterElements(@NotNull final PsiElement[] targetElements) {
    final Set<PsiElement> unique = new LinkedHashSet<>(Arrays.asList(targetElements));
    for (final PsiElement elt : targetElements) {
      ApplicationManager.getApplication().runReadAction(() -> {
        final PsiFile containingFile = elt.getContainingFile();
        LOG.assertTrue(containingFile != null, elt);
        PsiFile psiFile = containingFile.getOriginalFile();
        if (psiFile.getVirtualFile() == null) unique.remove(elt);
      });
    }
    // special case for Python (PY-237)
    // if the definition is the tree parent of the target element, filter out the target element
    for (int i = 1; i < targetElements.length; i++) {
      final PsiElement targetElement = targetElements[i];
      if (ReadAction.compute(() -> PsiTreeUtil.isAncestor(targetElement, targetElements[0], true))) {
        unique.remove(targetElements[0]);
        break;
      }
    }
    return PsiUtilCore.toPsiElementArray(unique);
  }

  protected boolean isSearchDeep() {
    return false;
  }

  private class ImplementationsUpdaterTask extends BackgroundUpdaterTask<ImplementationViewComponent> {
    private final String myCaption;
    private final Editor myEditor;
    @NotNull
    private final PsiElement myElement;
    private final boolean myIncludeSelf;
    private PsiElement[] myElements;

    private ImplementationsUpdaterTask(@NotNull PsiElement element, final Editor editor, final String caption, boolean includeSelf) {
      super(element.getProject(), ImplementationSearcher.SEARCHING_FOR_IMPLEMENTATIONS, null);
      myCaption = caption;
      myEditor = editor;
      myElement = element;
      myIncludeSelf = includeSelf;
    }

    @Override
    public String getCaption(int size) {
      return myCaption;
    }

    @Override
    protected void paintBusy(boolean paintBusy) {
      //todo notify busy
    }

    @Override
    protected void replaceModel(@NotNull List<PsiElement> data) {
      final PsiElement[] elements = myComponent.getElements();
      final int includeSelfIdx = myElement instanceof PomTargetPsiElement ? 0 : 1;
      final int startIdx = elements.length - includeSelfIdx;
      final PsiElement[] result = new PsiElement[data.size() + includeSelfIdx];
      System.arraycopy(elements, 0, result, 0, elements.length);
      System.arraycopy(PsiUtilCore.toPsiElementArray(data), startIdx, result, elements.length, data.size() - startIdx);
      myComponent.update(result, myComponent.getIndex());
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      final ImplementationSearcher.BackgroundableImplementationSearcher implementationSearcher =
        new ImplementationSearcher.BackgroundableImplementationSearcher() {
          @Override
          protected boolean isSearchDeep() {
            return ShowImplementationsAction.this.isSearchDeep();
          }

          @Override
          protected void processElement(PsiElement element) {
            if (!updateComponent(element)) {
              indicator.cancel();
            }
            indicator.checkCanceled();
          }

          @Override
          protected PsiElement[] filterElements(PsiElement element, PsiElement[] targetElements) {
            return ShowImplementationsAction.filterElements(targetElements);
          }
        };
      if (!myIncludeSelf) {
        myElements = getSelfAndImplementations(myEditor, myElement, implementationSearcher, false);
      }
      else {
        myElements = getSelfAndImplementations(myEditor, myElement, implementationSearcher);
      }
    }

    @Override
    public int getCurrentSize() {
      if (myElements != null) return myElements.length;
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
