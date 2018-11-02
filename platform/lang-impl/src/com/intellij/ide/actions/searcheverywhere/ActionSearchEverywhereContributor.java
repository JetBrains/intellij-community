// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoActionAction;
import com.intellij.ide.actions.SetShortcutAction;
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
import java.util.function.Function;

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
  public void fetchElements(@NotNull String pattern, boolean everywhere, @Nullable SearchEverywhereContributorFilter<Void> filter,
                            @NotNull ProgressIndicator progressIndicator, @NotNull Function<Object, Boolean> consumer) {
    if (StringUtil.isEmptyOrSpaces(pattern)) {
      return;
    }

    myProvider.filterElements(pattern, element -> {
      if (progressIndicator.isCanceled()) return false;

      if (!everywhere && element.value instanceof GotoActionModel.ActionWrapper && !((GotoActionModel.ActionWrapper) element.value).isAvailable()) {
        return true;
      }

      if (element == null) {
        LOG.error("Null action has been returned from model");
        return true;
      }

      return consumer.apply(element);
    });

  }

  @NotNull
  @Override
  public ListCellRenderer getElementsRenderer(@NotNull JList<?> list) {
    return new GotoActionModel.GotoActionListCellRenderer(myModel::getGroupName, true);
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
  public Object getDataForItem(@NotNull Object element, @NotNull String dataId) {
    if (SetShortcutAction.SELECTED_ACTION.is(dataId)) {
      return getAction((GotoActionModel.MatchedValue)element);
    }

    if (SearchEverywhereDataKeys.ITEM_STRING_DESCRIPTION.is(dataId)) {
      AnAction action = getAction((GotoActionModel.MatchedValue)element);
      return action == null ? null : action.getTemplatePresentation().getDescription();
    }

    return null;
  }

  @Override
  public boolean processSelectedItem(@NotNull Object selected, int modifiers, @NotNull String text) {
    selected = ((GotoActionModel.MatchedValue) selected).value;

    if (selected instanceof BooleanOptionDescription) {
      final BooleanOptionDescription option = (BooleanOptionDescription) selected;
      option.setOptionState(!option.isOptionEnabled());
      return false;
    }

    GotoActionAction.openOptionOrPerformAction(selected, text, myProject, myContextComponent);
    boolean inplaceChange = selected instanceof GotoActionModel.ActionWrapper
                            && ((GotoActionModel.ActionWrapper) selected).getAction() instanceof ToggleAction;
    return !inplaceChange;
  }

  @Nullable
  private static AnAction getAction(@NotNull GotoActionModel.MatchedValue element) {
    Object value = element.value;
    if (value instanceof GotoActionModel.ActionWrapper) {
      value = ((GotoActionModel.ActionWrapper)value).getAction();
    }
    return value instanceof AnAction ? (AnAction) value : null;
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
    public SearchEverywhereContributorFilter<Void> createFilter(AnActionEvent initEvent) {
      return null;
    }
  }
}
