// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

public abstract class AbstractGotoSEContributor<F> implements SearchEverywhereContributor<F> {

  protected final Project myProject;

  protected AbstractGotoSEContributor(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getSearchProviderId() {
    return getClass().getSimpleName();
  }

  private static final Logger LOG = Logger.getInstance(AbstractGotoSEContributor.class);

  @Override
  public ContributorSearchResult<Object> search(String pattern, boolean everywhere, SearchEverywhereContributorFilter<F> filter, ProgressIndicator progressIndicator, int elementsLimit) {
    if (!isDumbModeSupported() && DumbService.getInstance(myProject).isDumb()) {
      return ContributorSearchResult.empty();
    }

    FilteringGotoByModel<F> model = createModel(myProject);
    model.setFilterItems(filter.getSelectedElements());
    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(myProject, model, (PsiElement)null);
    ContributorSearchResult.Builder<Object> builder = ContributorSearchResult.builder();
    ApplicationManager.getApplication().runReadAction(() -> {
      popup.getProvider().filterElements(popup, pattern, everywhere, progressIndicator,
                                         o -> addFoundElement(o, model, builder, progressIndicator, elementsLimit)
      );
    });

    return builder.build();
  }

  protected boolean addFoundElement(Object element, ChooseByNameModel model, ContributorSearchResult.Builder<Object> resultBuilder,
                                    ProgressIndicator progressIndicator, int elementsLimit) {
    if (progressIndicator.isCanceled()) return false;
    if (element == null) {
      LOG.error("Null returned from " + model + " in " + this);
      return true;
    }

    if (resultBuilder.itemsCount() < elementsLimit ) {
      resultBuilder.addItem(element);
      return true;
    } else {
      resultBuilder.setHasMore(true);
      return false;
    }
  }

  //todo param is unnecessary #UX-1
  protected abstract FilteringGotoByModel<F> createModel(Project project);

  @Override
  public boolean showInFindResults() {
    return true;
  }

  @Override
  public boolean processSelectedItem(Object selected, int modifiers) {
    //todo maybe another elements types
    if (selected instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement) selected, (modifiers & InputEvent.SHIFT_MASK) != 0);
    }

    return true;
  }

  @Override
  public Object getDataForItem(Object element, String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return element;
    }

    return null;
  }

  protected boolean isDumbModeSupported() {
    return false;
  }
}
