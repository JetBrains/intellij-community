/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.AnonymousElementProvider;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

public class GotoClassAction extends GotoActionBase implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    if (!DumbService.getInstance(project).isDumb()) {
      super.actionPerformed(e);
    }
    else {
      DumbService.getInstance(project).showDumbModeNotification(IdeBundle.message("go.to.class.dumb.mode.message"));
      AnAction action = ActionManager.getInstance().getAction(GotoFileAction.ID);
      InputEvent event = ActionCommand.getInputEvent(GotoFileAction.ID);
      Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
      ActionManager.getInstance().tryToExecute(action, event, component, e.getPlace(), true);
    }
  }

  @Override
  public void gotoActionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.class");

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final GotoClassModel2 model = new GotoClassModel2(project);
    showNavigationPopup(e, model, new GotoActionCallback<Language>() {
      @Override
      protected ChooseByNameFilter<Language> createFilter(@NotNull ChooseByNamePopup popup) {
        return new ChooseByNameLanguageFilter(popup, model, GotoClassSymbolConfiguration.getInstance(project), project);
      }

      @Override
      public void elementChosen(ChooseByNamePopup popup, Object element) {
        ApplicationManager.getApplication().runReadAction(() -> {
          if (element instanceof PsiElement && ((PsiElement)element).isValid()) {
            PsiElement psiElement = getElement(((PsiElement)element), popup);
            psiElement = psiElement.getNavigationElement();
            VirtualFile file = PsiUtilCore.getVirtualFile(psiElement);

            if (file != null && popup.getLinePosition() != -1) {
              OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, popup.getLinePosition(), popup.getColumnPosition());
              Navigatable n = descriptor.setUseCurrentWindow(popup.isOpenInCurrentWindowRequested());
              if (n.canNavigate()) {
                n.navigate(true);
                return;
              }
            }

            if (file != null && popup.getMemberPattern() != null) {
              NavigationUtil.activateFileWithPsiElement(psiElement, !popup.isOpenInCurrentWindowRequested());
              Navigatable member = findMember(popup.getMemberPattern(), psiElement, file);
              if (member != null) {
                member.navigate(true);
              }
            }

            NavigationUtil.activateFileWithPsiElement(psiElement, !popup.isOpenInCurrentWindowRequested());
          }
          else {
            EditSourceUtil.navigate(((NavigationItem)element), true, popup.isOpenInCurrentWindowRequested());
          }
        });
      }
    }, IdeBundle.message("go.to.class.toolwindow.title"), true);
  }

  @Nullable
  private static Navigatable findMember(String pattern, PsiElement psiElement, VirtualFile file) {
    final PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(psiElement.getLanguage());
    final StructureViewBuilder builder = factory == null ? null : factory.getStructureViewBuilder(psiElement.getContainingFile());
    final FileEditor[] editors = FileEditorManager.getInstance(psiElement.getProject()).getEditors(file);
    if (builder == null || editors.length == 0) {
      return null;
    }

    final StructureView view = builder.createStructureView(editors[0], psiElement.getProject());
    try {
      final StructureViewTreeElement element = findElement(view.getTreeModel().getRoot(), psiElement, 4);
      if (element == null) {
        return null;
      }
      final MinusculeMatcher matcher = NameUtil.buildMatcher(pattern).build();
      int max = Integer.MIN_VALUE;
      Object target = null;
      for (TreeElement treeElement : element.getChildren()) {
        if (treeElement instanceof StructureViewTreeElement) {
          String presentableText = treeElement.getPresentation().getPresentableText();
          if (presentableText != null) {
            final int degree = matcher.matchingDegree(presentableText);
            if (degree > max) {
              max = degree;
              target = ((StructureViewTreeElement)treeElement).getValue();
            }
          }
        }
      }
      return target instanceof Navigatable ? (Navigatable)target : null;
    }
    finally {
      Disposer.dispose(view);
    }
  }

  @Nullable
  private static StructureViewTreeElement findElement(StructureViewTreeElement node, PsiElement element, int hopes) {
    final Object value = node.getValue();
    if (value instanceof PsiElement) {
      if (((PsiElement)value).isEquivalentTo(element)) return node;
      if (hopes != 0) {
        for (TreeElement child : node.getChildren()) {
          if (child instanceof StructureViewTreeElement) {
            final StructureViewTreeElement e = findElement((StructureViewTreeElement)child, element, hopes - 1);
            if (e != null) {
              return e;
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static PsiElement getElement(@NotNull PsiElement element, ChooseByNamePopup popup) {
    final String path = popup.getPathToAnonymous();
    if (path != null) {
      final String[] classes = path.split("\\$");
      List<Integer> indexes = new ArrayList<Integer>();
      for (String cls : classes) {
        if (cls.isEmpty()) continue;
        try {
          indexes.add(Integer.parseInt(cls) - 1);
        }
        catch (Exception e) {
          return element;
        }
      }
      PsiElement current = element;
      for (int index : indexes) {
        final PsiElement[] anonymousClasses = getAnonymousClasses(current);
        if (index >= 0 && index < anonymousClasses.length) {
          current = anonymousClasses[index];
        }
        else {
          return current;
        }
      }
      return current;
    }
    return element;
  }

  @NotNull
  private static PsiElement[] getAnonymousClasses(@NotNull PsiElement element) {
    for (AnonymousElementProvider provider : Extensions.getExtensions(AnonymousElementProvider.EP_NAME)) {
      final PsiElement[] elements = provider.getAnonymousElements(element);
      if (elements.length > 0) {
        return elements;
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  protected boolean hasContributors(DataContext dataContext) {
    return ChooseByNameRegistry.getInstance().getClassModelContributors().length > 0;
  }
}
