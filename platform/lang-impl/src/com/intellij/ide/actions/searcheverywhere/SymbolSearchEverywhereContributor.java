// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.ide.util.gotoByName.LanguageRef;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersStatisticsCollector.LangFilterCollector;
import static com.intellij.ide.actions.searcheverywhere.footer.ExtendedInfoImplKt.createPsiExtendedInfo;

/**
 * @author Konstantin Bulenkov
 */
public class SymbolSearchEverywhereContributor extends AbstractGotoSEContributor implements PossibleSlowContributor,
                                                                                            SearchEverywherePreviewProvider {

  private static final Logger LOG = Logger.getInstance(SymbolSearchEverywhereContributor.class);

  private final PersistentSearchEverywhereContributorFilter<LanguageRef> myFilter;

  @ApiStatus.Internal
  public SymbolSearchEverywhereContributor(@NotNull AnActionEvent event, @Nullable List<SearchEverywhereContributorModule> contributorModules) {
    super(event, contributorModules);
    myFilter = ClassSearchEverywhereContributor.createLanguageFilter(event.getRequiredData(CommonDataKeys.PROJECT));
  }

  public SymbolSearchEverywhereContributor(@NotNull AnActionEvent event) {
    super(event);
    myFilter = ClassSearchEverywhereContributor.createLanguageFilter(event.getRequiredData(CommonDataKeys.PROJECT));
  }

  @Override
  public @NotNull String getGroupName() {
    return IdeBundle.message("search.everywhere.group.name.symbols");
  }

  @Override
  public int getSortWeight() {
    return 300;
  }

  @Override
  public @Nullable ExtendedInfo createExtendedInfo() {
    final var vanillaInfo = createPsiExtendedInfo();
    final var contributorModules = getContributorModules();
    if (contributorModules == null || contributorModules.isEmpty()) return vanillaInfo;
    return contributorModules.getFirst().mixinExtendedInfo(vanillaInfo);
  }

  @Override
  protected @NotNull FilteringGotoByModel<LanguageRef> createModel(@NotNull Project project) {
    final var contribModules = getContributorModules();
    if (contribModules != null) {
      for (final var it : contribModules) {
        var customModel = it.createCustomModel(project, this, null);
        if (customModel != null) return customModel;
      }
    }

    GotoSymbolModel2 model = new GotoSymbolModel2(project, this);
    if (myFilter != null) {
      model.setFilterItems(myFilter.getSelectedElements());
    }
    return model;
  }

  @ApiStatus.Internal
  @Override
  protected @NotNull FilteringGotoByModel<?> createModelWithOperationDisposable(@NotNull Project project, @Nullable Disposable operationDisposable) {
    final var contribModules = getContributorModules();
    if (contribModules != null) {
      for (final var it : contribModules) {
        var customModel = it.createCustomModel(project, this, operationDisposable);
        if (customModel != null) return customModel;
      }
    }

    GotoSymbolModel2 model = new GotoSymbolModel2(project, this);
    if (myFilter != null) {
      model.setFilterItems(myFilter.getSelectedElements());
    }
    return model;
  }

  @Override
  public @NotNull List<AnAction> getActions(@NotNull Runnable onChanged) {
    return doGetActions(myFilter, new LangFilterCollector(), onChanged);
  }

  public static final class Factory implements SearchEverywhereContributorFactory<Object> {
    @Override
    public @NotNull SearchEverywhereContributor<Object> createContributor(@NotNull AnActionEvent initEvent) {
      return PSIPresentationBgRendererWrapper.wrapIfNecessary(new SymbolSearchEverywhereContributor(initEvent));
    }
  }
}
