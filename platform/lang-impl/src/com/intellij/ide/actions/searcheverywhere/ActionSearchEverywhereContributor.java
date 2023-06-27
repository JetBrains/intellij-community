// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoActionAction;
import com.intellij.ide.actions.SetShortcutAction;
import com.intellij.ide.actions.searcheverywhere.footer.ActionExtendedInfoKt;
import com.intellij.ide.actions.searcheverywhere.footer.ActionHistoryManager;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.util.gotoByName.GotoActionItemProvider;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText;

public class ActionSearchEverywhereContributor implements WeightedSearchEverywhereContributor<GotoActionModel.MatchedValue>,
                                                          LightEditCompatible, SearchEverywhereExtendedInfoProvider {

  private static final Logger LOG = Logger.getInstance(ActionSearchEverywhereContributor.class);

  private final Project myProject;
  private final WeakReference<Component> myContextComponent;
  private final GotoActionModel myModel;
  private final GotoActionItemProvider myProvider;
  protected boolean myDisabledActions;

  public ActionSearchEverywhereContributor(Project project, Component contextComponent, Editor editor) {
    myProject = project;
    myContextComponent = new WeakReference<>(contextComponent);
    myModel = new GotoActionModel(project, contextComponent, editor);
    myProvider = new GotoActionItemProvider(myModel);
  }

  @NotNull
  @Override
  public String getGroupName() {
    return IdeBundle.message("search.everywhere.group.name.actions");
  }

  @Nullable
  @Override
  public String getAdvertisement() {
    if (Registry.is("search.everywhere.footer.extended.info")) return null;

    ShortcutSet altEnterShortcutSet = getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
    @NlsSafe String altEnter = getFirstKeyboardShortcutText(altEnterShortcutSet);
    return IdeBundle.message("press.0.to.assign.a.shortcut", altEnter);
  }

  @Nls
  @Override
  public @Nullable ExtendedInfo createExtendedInfo() {
    return ActionExtendedInfoKt.createActionExtendedInfo(myProject);
  }


  @NlsContexts.Checkbox
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
  public void fetchWeightedElements(@NotNull String pattern,
                                    @NotNull ProgressIndicator progressIndicator,
                                    @NotNull Processor<? super FoundItemDescriptor<GotoActionModel.MatchedValue>> consumer) {

    if (StringUtil.isEmptyOrSpaces(pattern)) {
      if (Registry.is("search.everywhere.recents")) {
        Set<String> actionIDs = ActionHistoryManager.getInstance().getState().getIds();
        Predicate<GotoActionModel.MatchedValue> actionDegreePredicate =
          element -> {
            if (!myDisabledActions && !((GotoActionModel.ActionWrapper)element.value).isAvailable()) return true;

            AnAction action = getAction(element);
            if (action == null) return true;

            String id = ActionManager.getInstance().getId(action);
            int degree = actionIDs.stream().toList().indexOf(id);

            return consumer.process(new FoundItemDescriptor<>(element, degree));
          };

        myProvider.processActions(pattern, actionDegreePredicate, new HashSet<>(actionIDs));
      }
      return;
    }

    ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      myProvider.filterElements(pattern, element -> {
        if (progressIndicator.isCanceled()) return false;

        if (element == null) {
          LOG.error("Null action has been returned from model");
          return true;
        }

        final boolean isActionWrapper = element.value instanceof GotoActionModel.ActionWrapper;
        if (!myDisabledActions && isActionWrapper && !((GotoActionModel.ActionWrapper)element.value).isAvailable()) {
          return true;
        }

        final var descriptor = new FoundItemDescriptor<GotoActionModel.MatchedValue>(element, element.getMatchingDegree());
        return consumer.process(descriptor);
      });
    }, progressIndicator);
  }

  @NotNull
  @Override
  public List<AnAction> getActions(@NotNull Runnable onChanged) {
    return Collections.singletonList(new CheckBoxSearchEverywhereToggleAction(includeNonProjectItemsText()) {
      @Override
      public boolean isEverywhere() {
        return myDisabledActions;
      }

      @Override
      public void setEverywhere(boolean state) {
        myDisabledActions = state;
        onChanged.run();
      }
    });
  }

  @NotNull
  @Override
  public ListCellRenderer<? super GotoActionModel.MatchedValue> getElementsRenderer() {
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
  public Object getDataForItem(@NotNull GotoActionModel.MatchedValue element, @NotNull String dataId) {
    return SetShortcutAction.SELECTED_ACTION.is(dataId) ? getAction(element) : null;
  }

  @Override
  public @Nullable String getItemDescription(@NotNull GotoActionModel.MatchedValue element) {
    AnAction action = getAction(element);
    if (action == null) {
      return null;
    }
    String description = action.getTemplatePresentation().getDescription();
    if (UISettings.getInstance().getShowInplaceCommentsInternal()) {
      String presentableId = StringUtil.notNullize(ActionManager.getInstance().getId(action), "class: " + action.getClass().getName());
      return String.format("[%s] %s", presentableId, StringUtil.notNullize(description));
    }
    return description;
  }

  @Override
  public boolean processSelectedItem(@NotNull GotoActionModel.MatchedValue item, int modifiers, @NotNull String text) {
    if (modifiers == InputEvent.ALT_MASK) {
      showAssignShortcutDialog(myProject, item);
      return true;
    }

    Object selected = item.value;

    if (selected instanceof BooleanOptionDescription option) {
      if (selected instanceof BooleanOptionDescription.RequiresRebuild) {
        myModel.clearCaches(); // release references to plugin actions so that the plugin can be unloaded successfully
        myProvider.clearIntentions();
      }
      option.setOptionState(!option.isOptionEnabled());
      return false;
    }

    if (Registry.is("search.everywhere.recents")) {
      saveRecentAction(item);
    }

    GotoActionAction.openOptionOrPerformAction(selected, text, myProject, myContextComponent.get(), modifiers);
    boolean inplaceChange = selected instanceof GotoActionModel.ActionWrapper
                            && ((GotoActionModel.ActionWrapper)selected).getAction() instanceof ToggleAction;
    return !inplaceChange;
  }

  private static void saveRecentAction(@NotNull GotoActionModel.MatchedValue selected) {
    AnAction action = getAction(selected);
    if (action == null) return;

    String id = ActionManager.getInstance().getId(action);
    if (id == null) return;

    Set<String> ids = ActionHistoryManager.getInstance().getState().getIds();
    if (ids.size() < Registry.intValue("search.everywhere.recents.limit")) {
      ids.add(id);
    }
  }

  @Nullable
  private static AnAction getAction(@NotNull GotoActionModel.MatchedValue element) {
    Object value = element.value;
    if (value instanceof GotoActionModel.ActionWrapper) {
      value = ((GotoActionModel.ActionWrapper)value).getAction();
    }
    return value instanceof AnAction ? (AnAction)value : null;
  }

  public static void showAssignShortcutDialog(@Nullable Project myProject, @NotNull GotoActionModel.MatchedValue value) {
    AnAction action = getAction(value);
    if (action == null) return;

    String id = ActionManager.getInstance().getId(action);

    Keymap activeKeymap = Optional.ofNullable(KeymapManager.getInstance())
      .map(KeymapManager::getActiveKeymap)
      .orElse(null);
    if (activeKeymap == null) return;

    ApplicationManager.getApplication().invokeLater(() -> {
      Window window = myProject != null
                      ? WindowManager.getInstance().suggestParentWindow(myProject)
                      : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window == null) return;

      KeymapPanel.addKeyboardShortcut(id, ActionShortcutRestrictions.getInstance().getForActionId(id), activeKeymap, window);
    });
  }

  public static class Factory implements SearchEverywhereContributorFactory<GotoActionModel.MatchedValue> {
    @NotNull
    @Override
    public SearchEverywhereContributor<GotoActionModel.MatchedValue> createContributor(@NotNull AnActionEvent initEvent) {
      return new ActionSearchEverywhereContributor(
        initEvent.getProject(),
        initEvent.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
        initEvent.getData(CommonDataKeys.EDITOR));
    }
  }

  @Override
  public boolean isEmptyPatternSupported() {
    return true;
  }
}