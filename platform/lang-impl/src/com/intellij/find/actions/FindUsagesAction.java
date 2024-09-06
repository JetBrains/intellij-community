// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.FindSettings;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.usages.api.SearchTarget;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.EmptyFindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.find.actions.FindUsagesKt.findUsages;
import static com.intellij.find.actions.ResolverKt.allTargets;
import static com.intellij.find.actions.ResolverKt.findShowUsages;

public class FindUsagesAction extends AnAction {

  /**
   * @see SearchTargetsDataRule
   */
  @Experimental
  public static final DataKey<Collection<SearchTarget>> SEARCH_TARGETS = DataKey.create("search.targets");

  public FindUsagesAction() {
    setInjectedContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected boolean toShowDialog() {
    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    DataContext dataContext = e.getDataContext();
    SearchScope searchScope = FindUsagesOptions.findScopeByName(
      project, dataContext, FindSettings.getInstance().getDefaultScopeName()
    );
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    RelativePoint popupLocation = editor != null
                                  ? popupFactory.guessBestPopupLocation(editor)
                                  : popupFactory.guessBestPopupLocation(dataContext);
    ReadAction.nonBlocking(() -> allTargets(dataContext))
      .expireWith(project)
      .finishOnUiThread(ModalityState.nonModal(),
                        allTargets -> findShowUsages(project, editor, popupLocation, allTargets, FindBundle.message("find.usages.ambiguous.title"),
        new UsageVariantHandler() {

          @Override
          public void handleTarget(@NotNull SearchTarget target) {
            findUsages(toShowDialog(), project, searchScope, target);
          }

          @Override
          public void handlePsi(@NotNull PsiElement element) {
            startFindUsages(element);
          }
        }
      ))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  protected void startFindUsages(@NotNull PsiElement element) {
    FindManager.getInstance(element.getProject()).findUsages(element);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    updateFindUsagesAction(event);
  }

  private static boolean isEnabled(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null ||
        EditorGutter.KEY.getData(dataContext) != null ||
        Boolean.TRUE.equals(dataContext.getData(CommonDataKeys.EDITOR_VIRTUAL_SPACE))) {
      return false;
    }
    return canFindUsages(project, dataContext) ||
           !allTargets(dataContext).isEmpty();
  }

  private static boolean canFindUsages(@NotNull Project project, @NotNull DataContext dataContext) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return false;
    }
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      return false;
    }
    Language language = PsiUtilBase.getLanguageInEditor(editor, project);
    if (language == null) {
      language = file.getLanguage();
    }
    return !(LanguageFindUsages.INSTANCE.forLanguage(language) instanceof EmptyFindUsagesProvider);
  }

  public static void updateFindUsagesAction(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    boolean enabled = isEnabled(dataContext);
    presentation.setVisible(enabled || !event.isFromContextMenu());
    presentation.setEnabled(enabled);
  }

  public static final class ShowSettingsAndFindUsages extends FindUsagesAction {
    @Override
    protected void startFindUsages(@NotNull PsiElement element) {
      FindManager.getInstance(element.getProject()).findUsages(element, true);
    }

    @Override
    protected boolean toShowDialog() {
      return true;
    }
  }
}
