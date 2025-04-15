package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.extensions.ExtractorScripts;
import com.intellij.database.extractors.DataExtractorFactory;
import com.intellij.database.extractors.DataExtractorProperties;
import com.intellij.database.extractors.ExtractorsHelper;
import com.intellij.database.settings.CsvSettings;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.intellij.database.extractors.DataExtractorFactories.*;
import static com.intellij.database.util.DataGridUIUtil.updateAllToolbarsUnder;

/**
 * @author Gregory.Shrago
 */
public class ChooseExtractorAction extends ActionGroup implements GridAction, DumbAware {
  private final String myPopupGroupId;

  @SuppressWarnings("unused")
  public ChooseExtractorAction() {
    this("Console.TableResult.ChooseExtractor.Group");
  }

  public ChooseExtractorAction(@NotNull String popupGroupId) {
    setPopup(true);
    getTemplatePresentation().setPerformGroup(true);
    myPopupGroupId = popupGroupId;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return EMPTY_ARRAY;
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    boolean enabled = dataGrid != null;
    e.getPresentation().setEnabledAndVisible(enabled);
    if (!enabled || ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      Presentation p = e.getPresentation();
      Presentation tp = getTemplatePresentation();
      p.setText(tp.getText());
      p.setDescription(tp.getDescription());
      return;
    }
    List<DataExtractorFactory> scripts = getExtractorScripts(ExtractorsHelper.getInstance(dataGrid), null);

    String displayName = getDisplayName(getExtractorFactory(dataGrid, GridUtil::suggestPlugin), scripts);
    String trimmedName = StringUtil.firstLast(displayName, 24);
    if (e.isFromContextMenu()) {
      e.getPresentation().setText(getTemplatePresentation().getText() + " (" + trimmedName + ")", false);
    }
    else {
      e.getPresentation().setText(trimmedName, false);
      e.getPresentation().setDescription(displayName);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    InputEvent inputEvent = e.getInputEvent();
    JComponent button = inputEvent == null ? null : ObjectUtils.tryCast(inputEvent.getSource(), JComponent.class);
    ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(myPopupGroupId);
    JBPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      DataGridBundle.message("settings.ChooseExtractorAction.title"), actionGroup, e.getDataContext(),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
    popup.setAdText(toHtml(DataGridBundle.message("settings.extractors.PopupAd")), SwingConstants.LEFT);
    if (button != null) {
      popup.showUnderneathOf(button);
    }
    else {
      popup.showInBestPositionFor(e.getDataContext());
    }
  }

  protected @Nls @NotNull String toHtml(@Nls @NotNull String text) {
    return "<html>" + text.replaceAll("\n", "<br>") + "</html>";
  }

  private abstract static class GroupBase extends ActionGroup implements DumbAware {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      DataGrid dataGrid = e == null ? null : e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (dataGrid == null) return EMPTY_ARRAY;

      JBIterable<AnAction> children = getChildrenImpl(dataGrid, (factory, factories) -> createAction(factory, factories, dataGrid));
      if (ActionPlaces.EDITOR_POPUP.equals(e.getPlace())) {
        // titled separators look awful in an ordinary JMenu
        return children.map(o -> o instanceof Separator ? Separator.getInstance() : o).toArray(EMPTY_ARRAY);
      }
      return children.toArray(EMPTY_ARRAY);
    }

    private static @NotNull SelectExtractorAction createAction(@NotNull DataExtractorFactory factory,
                                                               List<? extends DataExtractorFactory> factories,
                                                               @NotNull DataGrid grid) {
      return new SelectExtractorAction(factory, factories, (e) -> {
        GridHelper.get(grid).syncExtractorsInNotebook(grid, factory);
      });
    }

    protected abstract @NotNull JBIterable<AnAction> getChildrenImpl(@NotNull DataGrid grid,
                                                                     BiFunction<? super DataExtractorFactory, List<? extends DataExtractorFactory>, ? extends AnAction> function);
  }

  public static class BuiltInGroup extends GroupBase {
    @SuppressWarnings("BoundedWildcard")
    @Override
    protected @NotNull JBIterable<AnAction> getChildrenImpl(@NotNull DataGrid grid,
                                                            BiFunction<? super DataExtractorFactory, List<? extends DataExtractorFactory>, ? extends AnAction> function) {
      List<DataExtractorFactory> factories = JBIterable.from(getBuiltInFactories(grid))
        .filter(DataExtractorFactory.class)
        .filter(DataExtractorFactory::supportsText)
        .toList();
      //noinspection DialogTitleCapitalization
      return JBIterable.<AnAction>of(Separator.create(DataGridBundle.message("settings.extractors.BuiltIn")))
        .append(JBIterable.from(factories).map(f -> function.apply(f, factories)));
    }
  }

  public static class CsvGroup extends GroupBase {
    @SuppressWarnings("BoundedWildcard")
    @Override
    protected @NotNull JBIterable<AnAction> getChildrenImpl(@NotNull DataGrid grid,
                                                            BiFunction<? super DataExtractorFactory, List<? extends DataExtractorFactory>, ? extends AnAction> function) {
      List<DataExtractorFactory> factories = getCsvFormats(CsvSettings.getSettings());
      return JBIterable.<AnAction>of(Separator.create(DataGridBundle.message("settings.extractors.CSV")))
        .append(JBIterable.from(factories).filter(DataExtractorFactory.class).map(f -> function.apply(f, factories)));
    }
  }

  public static class ScriptedGroup extends GroupBase {
    @SuppressWarnings("BoundedWildcard")
    @Override
    protected @NotNull JBIterable<AnAction> getChildrenImpl(@NotNull DataGrid grid,
                                                            BiFunction<? super DataExtractorFactory, List<? extends DataExtractorFactory>, ? extends AnAction> function) {
      List<DataExtractorFactory> factories = getExtractorScripts(ExtractorsHelper.getInstance(grid), GridUtil::suggestPlugin);
      return JBIterable.<AnAction>of(Separator.create(DataGridBundle.message("settings.extractors.ScriptExtractors")))
        .append(JBIterable.from(factories)
                  .sort(Comparator.comparing(s -> StringUtil.toLowerCase(s.getName())))
                  .filter(DataExtractorFactory.class).map(f -> function.apply(f, factories)));
    }
  }

  public static final class SelectExtractorAction extends ToggleAction implements DumbAware {
    private final DataExtractorFactory factory;
    private final Consumer<AnActionEvent> myOnExtractorSelected;

    public SelectExtractorAction(@NotNull DataExtractorFactory factory,
                                 List<? extends DataExtractorFactory> factories,
                                 @Nullable Consumer<AnActionEvent> onExtractorSelected) {
      super(StringUtil.escapeMnemonics(getDisplayName(factory, factories)));
      this.factory = factory;
      myOnExtractorSelected = onExtractorSelected;
      getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (dataGrid == null) {
        return false;
      }
      // That is fine in terms of speed
      return getExtractorFactory(dataGrid, GridUtil::suggestPlugin).getId().equals(factory.getId());
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (dataGrid == null) return;
      setExtractorFactory(dataGrid, factory);
      Project project = dataGrid.getProject();
      DataExtractorProperties.setCurrentExportExtractorFactory(project, factory);
      DataExtractorProperties.setCurrentExtractorFactory(project, factory);
      if (myOnExtractorSelected != null) myOnExtractorSelected.accept(e);
      updateAllToolbarsUnder(dataGrid.getPanel().getComponent());
    }
  }

  public static class GoToScriptsDirectoryAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      File scriptsDir = ExtractorScripts.getExtractorScriptsDirectory();
      e.getPresentation().setEnabledAndVisible(project != null && scriptsDir != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      File scriptsDir = ExtractorScripts.getExtractorScriptsDirectory();
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
}
