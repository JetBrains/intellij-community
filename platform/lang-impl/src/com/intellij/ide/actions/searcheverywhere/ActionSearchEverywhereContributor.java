// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoActionAction;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.GotoActionItemProvider;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

//todo not extends AbstractGotoSEContributor #UX-1
public class ActionSearchEverywhereContributor extends AbstractGotoSEContributor {
  private final Component myContextComponent;
  private final Editor myEditor;
  private final GotoActionModel myModel;
  private final GotoActionItemProvider myProvider;

  public ActionSearchEverywhereContributor(Project project, Component contextComponent, Editor editor) {
    super(project);
    this.myContextComponent = contextComponent;
    this.myEditor = editor;
    myModel = new GotoActionModel(project, contextComponent, editor);
    myProvider = new GotoActionItemProvider(myModel);
  }

  @Override
  protected ChooseByNameModel createModel(Project project) {
    return new GotoActionModel(project, myContextComponent, myEditor);
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
  public ContributorSearchResult<Object> search(String pattern,
                                                boolean everywhere,
                                                ProgressIndicator progressIndicator,
                                                int elementsLimit) {
    if (StringUtil.isEmptyOrSpaces(pattern)) {
      return ContributorSearchResult.empty();
    }

    ContributorSearchResult.Builder<Object> builder = ContributorSearchResult.builder();
    myProvider.filterElements(pattern, o -> {
                              if (!everywhere && o.value instanceof GotoActionModel.ActionWrapper && !((GotoActionModel.ActionWrapper)o.value).isAvailable()) {
                                return true;
                              }
                              return addFoundElement(o, myModel, builder, progressIndicator, elementsLimit);
                            });

    return builder.build();
  }

  @Override
  public ListCellRenderer getElementsRenderer() {
    return myModel.getListCellRenderer();
  }

  @Override
  public boolean showInFindResults() {
    return false;
  }

  @Override
  protected boolean isDumbModeSupported() {
    return true;
  }

  @Override
  public boolean processSelectedItem(Object selected, int modifiers) {
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

  @Override
  protected Object getItemData(String dataId, Object element) {
    return null;
  }

  public static class Factory implements SearchEverywhereContributorFactory {

    @Override
    public SearchEverywhereContributor createContributor(AnActionEvent initEvent) {
      return new ActionSearchEverywhereContributor(
        initEvent.getProject(),
        initEvent.getData(PlatformDataKeys.CONTEXT_COMPONENT),
        initEvent.getData(CommonDataKeys.EDITOR));
    }
  }
}
