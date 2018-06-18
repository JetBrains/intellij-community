// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoActionAction;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.util.gotoByName.GotoActionItemProvider;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ActionSearchEverywhereContributor implements SearchEverywhereContributor<Void> {
  private static final Logger LOG = Logger.getInstance(ActionSearchEverywhereContributor.class);

  private final Project myProject;
  private final Component myContextComponent;
  private final GotoActionModel myModel;
  private final GotoActionItemProvider myProvider;

  public ActionSearchEverywhereContributor(Project project, Component contextComponent, Editor editor) {
    myProject = project;
    myContextComponent = contextComponent;
    myModel = new GotoActionModel(project, contextComponent, editor);
    myProvider = new GotoActionItemProvider(myModel);
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Actions";
  }

  @Override
  public String includeNonProjectItemsText() {
    return IdeBundle.message("checkbox.disabled.included");
  }

  @Override
  public int getSortWeight() {
    return 400;
  }

  @Override
  public boolean isShownInSeparateTab() {
    return true;
  }

  @Override
  public ContributorSearchResult<Object> search(String pattern,
                                                boolean everywhere,
                                                SearchEverywhereContributorFilter<Void> filter,
                                                ProgressIndicator progressIndicator,
                                                int elementsLimit) {
    if (StringUtil.isEmptyOrSpaces(pattern)) {
      return ContributorSearchResult.empty();
    }

    ContributorSearchResult.Builder<Object> builder = ContributorSearchResult.builder();
    myProvider.filterElements(pattern, element -> {
      if (progressIndicator.isCanceled()) return false;

      if (!everywhere && element.value instanceof GotoActionModel.ActionWrapper && !((GotoActionModel.ActionWrapper) element.value).isAvailable()) {
        return true;
      }

      if (element == null) {
        LOG.error("Null action has been returned from model");
        return true;
      }

      if (builder.itemsCount() < elementsLimit) {
        builder.addItem(element);
        return true;
      }
      else {
        builder.setHasMore(true);
        return false;
      }
    });

    return builder.build();
  }

  @Override
  public ListCellRenderer getElementsRenderer(JList<?> list) {
    return myModel.getListCellRenderer();
  }

  @Override
  public boolean showInFindResults() {
    return false;
  }

  @NotNull
  @Override
  public String getSearchProviderId() {
    return ActionSearchEverywhereContributor.class.getSimpleName();
  }

  @Override
  public Object getDataForItem(Object element, String dataId) {
    return null;
  }

  @Override
  public boolean processSelectedItem(Object selected, int modifiers, String text) {
    selected = ((GotoActionModel.MatchedValue) selected).value;

    if (selected instanceof BooleanOptionDescription) {
      final BooleanOptionDescription option = (BooleanOptionDescription) selected;
      option.setOptionState(!option.isOptionEnabled());
      return false;
    }

    GotoActionAction.openOptionOrPerformAction(selected, "", myProject, myContextComponent);
    boolean inplaceChange = selected instanceof GotoActionModel.ActionWrapper
                            && ((GotoActionModel.ActionWrapper) selected).getAction() instanceof ToggleAction;
    return !inplaceChange;
  }

  public static class Factory implements SearchEverywhereContributorFactory<Void> {

    @NotNull
    @Override
    public SearchEverywhereContributor<Void> createContributor(AnActionEvent initEvent) {
      return new ActionSearchEverywhereContributor(
        initEvent.getProject(),
        initEvent.getData(PlatformDataKeys.CONTEXT_COMPONENT),
        initEvent.getData(CommonDataKeys.EDITOR));
    }

    @Nullable
    @Override
    public SearchEverywhereContributorFilter<Void> createFilter() {
      return null;
    }
  }
}
