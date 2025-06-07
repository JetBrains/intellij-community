package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.AggregatorWidget;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.extensions.ExtractorScripts;
import com.intellij.database.extractors.*;
import com.intellij.database.run.ui.*;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.intellij.database.extensions.ExtensionScriptsUtil.showEngineNotFoundBalloon;
import static com.intellij.database.extractors.DataExtractorFactories.getDisplayName;
import static com.intellij.database.run.ui.EditMaximizedViewKt.EDIT_MAXIMIZED_KEY;

public final class ChooseAggregatorsAction {
  public static class ScriptedGroup extends ActionGroup implements DumbAware {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      EditMaximizedView view = e == null ? null : e.getData(EDIT_MAXIMIZED_KEY);
      DataGrid grid = e == null ? null : e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (view == null || grid == null) return EMPTY_ARRAY;
      AggregateView viewer = (AggregateView)view.getAggregateViewer();
      if (viewer == null) return EMPTY_ARRAY;

      JBIterable<AnAction> children = getChildrenImpl(grid, new BiFunction<>() {
        final List<String> selectedIdList = viewer.getEnabledAggregatorsScripts();

        @Override
        public AnAction apply(DataAggregatorFactory factory, List<? extends DataAggregatorFactory> factories) {
          String id = factory.getId();
          return new SelectAggregatorAction(factory, selectedIdList.contains(id), factories, () -> GridUtil.getSettings(grid));
        }
      });
      if (ActionPlaces.EDITOR_POPUP.equals(e.getPlace())) {
        return children.map(o -> o instanceof Separator ? Separator.getInstance() : o).toArray(EMPTY_ARRAY);
      }
      return children.toArray(EMPTY_ARRAY);
    }
  }

  @SuppressWarnings("BoundedWildcard")
  public static @NotNull JBIterable<AnAction> getChildrenImpl(@NotNull DataGrid grid, BiFunction<? super DataAggregatorFactory, List<? extends DataAggregatorFactory>, ? extends AnAction> function) {
    List<DataAggregatorFactory> scripts = DataExtractorFactories.getAggregatorScripts(ExtractorsHelper.getInstance(grid), GridUtil::suggestPlugin);
    return JBIterable.<AnAction>of(Separator.create(DataGridBundle.message("settings.aggregators.ScriptAggregators")))
      .append(JBIterable.from(scripts)
                .sort(Comparator.comparing(s -> StringUtil.toLowerCase(s.getName())))
                .map(s -> function.apply(s, scripts)));
  }

  private static void checkEnabled(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    EditMaximizedView view = e.getData(EDIT_MAXIMIZED_KEY);
    File scriptsDir = ExtractorScripts.getAggregatorScriptsDirectory();
    if (view == null || scriptsDir == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    AggregateView viewer = (AggregateView)view.getAggregateViewer();
    if (viewer == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(project != null && view.getCurrentTabInfoProvider() instanceof AggregatesTabInfoProvider);
  }

  public static class GoToScriptsDirectoryAction extends DumbAwareAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      checkEnabled(e);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      File scriptsDir = ExtractorScripts.getAggregatorScriptsDirectory();
      if (project == null || scriptsDir == null) return;

      var toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Project");
      if (toolWindow != null) {
        toolWindow.activate(null);
      }

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(scriptsDir);
        if (virtualFile == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
          PsiNavigationSupport.getInstance().createNavigatable(project, virtualFile, -1).navigate(true);
        });
      });
    }
  }

  public static final class SelectAggregatorAction extends ToggleAction implements DumbAware {
    final DataAggregatorFactory factory;
    final boolean selected;
    private final Supplier<DataGridSettings> mySettings;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    SelectAggregatorAction(@NotNull DataAggregatorFactory factory, boolean selected, @NotNull List<? extends DataAggregatorFactory> scripts, @NotNull Supplier<DataGridSettings> settings) {
      super(StringUtil.escapeMnemonics(getDisplayName(factory, scripts)));
      this.factory = factory;
      this.selected = selected;
      mySettings = settings;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      checkEnabled(e);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return selected;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      EditMaximizedView view = e.getData(EDIT_MAXIMIZED_KEY);
      if (view == null) return;
      AggregateView viewer = (AggregateView)view.getAggregateViewer();
      if (viewer == null) return;
      viewer.setAggregatorSelection(factory.getId(), state);
      ApplicationManager.getApplication().invokeLater(() -> viewer.update(null));
      DataGridSettings settings = mySettings.get();
      if (settings != null) {
        settings.setDisabledAggregators(viewer.getDisabledAggregatorsScripts());
        settings.fireChanged();
      }
    }
  }

  public static final class SelectSingleAggregatorAction extends ToggleAction implements DumbAware {
    private final Project myProject;
    final TableAggregatorWidgetHelper helper;
    final DataAggregatorFactory script;
    private final Supplier<@Nullable DataGridSettings> mySettingsSupplier;
    final StatusBar statusBar;
    @Nullable Aggregator aggregator;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    public SelectSingleAggregatorAction(@NotNull DataGrid grid,
                                        @NotNull StatusBar statusBar,
                                        @NotNull TableAggregatorWidgetHelper helper,
                                        @NotNull DataAggregatorFactory script,
                                        @NotNull List<? extends DataAggregatorFactory> factories,
                                        @NotNull Supplier<DataGridSettings> settingsSupplier) {
      super(StringUtil.escapeMnemonics(getDisplayName(script, factories)));
      myProject = grid.getProject();
      this.statusBar = statusBar;
      this.helper = helper;
      this.script = script;
      mySettingsSupplier = settingsSupplier;
      ApplicationManager.getApplication().invokeLater(() -> {
        ExtractorConfig config = ExtractorsHelper.getInstance(grid).createExtractorConfig(grid, grid.getObjectFormatter());
        DataExtractor extractor = script.createAggregator(config);
        if (extractor == null) return;
        aggregator = new Aggregator(grid, extractor, script.getSimpleName(), script.getName());
      });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return helper.getAggregator() != null && StringUtil.equals(helper.getAggregator().getName(), script.getId());
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      DataGridSettings settings = mySettingsSupplier.get();
      if (state) {
        if (aggregator == null) {
          BaseExtractorsHelper.Script s = ObjectUtils.tryCast(script, BaseExtractorsHelper.Script.class);
          String scriptExtension = s == null ? null : FileUtilRt.getExtension(s.getScriptFileName());
          if (scriptExtension != null) {
            showEngineNotFoundBalloon(myProject, GridUtil::suggestPlugin, scriptExtension);
          }
        }
        helper.setAggregator(aggregator);
        if (settings != null) settings.setWidgetAggregator(aggregator == null ? null : aggregator.getName());
      }
      else {
        helper.setAggregator(null);
        if (settings != null) settings.setWidgetAggregator(null);
      }
      statusBar.updateWidget(AggregatorWidget.ID);
      if (settings != null) settings.fireChanged();
    }
  }

  public static class EnableAllAggregatorsAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      checkEnabled(e);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (grid == null) return;
      updateAllAggregatorsState(e, DataExtractorFactories.getAggregatorScripts(ExtractorsHelper.getInstance(grid), null), true);
    }
  }

  public static class DisableAllAggregatorsAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      checkEnabled(e);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (grid == null) return;
      updateAllAggregatorsState(e, DataExtractorFactories.getAggregatorScripts(ExtractorsHelper.getInstance(grid), null), false);
    }
  }

  private static void updateAllAggregatorsState(@NotNull AnActionEvent e,
                                                List<DataAggregatorFactory> aggregators,
                                                boolean status) {
    EditMaximizedView view = e.getData(EDIT_MAXIMIZED_KEY);
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (view == null || grid == null) return;
    AggregateView viewer = (AggregateView)view.getAggregateViewer();
    if (viewer == null) return;
    for (DataAggregatorFactory aggregator : aggregators) {
      viewer.setAggregatorSelection(aggregator.getName(), status);
    }
    ApplicationManager.getApplication().invokeLater(() -> viewer.update(null));
    DataGridSettings settings = GridUtil.getSettings(grid);
    if (settings != null) {
      settings.setDisabledAggregators(viewer.getDisabledAggregatorsScripts());
      settings.fireChanged();
    }
  }
}

