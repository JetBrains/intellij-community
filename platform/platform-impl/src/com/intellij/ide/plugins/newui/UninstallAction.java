// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.PrepareToUninstallResult;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.xml.util.XmlStringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.exists;
import static com.intellij.util.containers.ContainerUtil.map;

final class UninstallAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

  private static final ShortcutSet SHORTCUT_SET;

  static {
    ShortcutSet deleteShortcutSet = EventHandler.getShortcuts(IdeActions.ACTION_EDITOR_DELETE);
    SHORTCUT_SET = deleteShortcutSet != null ?
                   deleteShortcutSet :
                   new CustomShortcutSet(EventHandler.DELETE_CODE);
  }

  private final @NotNull JComponent myUiParent;
  private final @NotNull Runnable myOnFinishAction;
  private final boolean myDynamicTitle;

  UninstallAction(@NotNull PluginModelFacade pluginModelFacade,
                  boolean showShortcut,
                  @NotNull JComponent uiParent,
                  @NotNull List<? extends C> selection,
                  @NotNull Function<? super C, PluginUiModel> pluginModelGetter,
                  @NotNull Runnable onFinishAction) {
    //noinspection unchecked
    super(IdeBundle.message(isBundledUpdate(selection, (Function<Object, PluginUiModel>)pluginModelGetter)
                            ? "plugins.configurable.uninstall.bundled.update"
                            : "plugins.configurable.uninstall"),
          pluginModelFacade,
          showShortcut,
          selection,
          pluginModelGetter);

    myUiParent = uiParent;
    myOnFinishAction = onFinishAction;
    myDynamicTitle = selection.size() == 1 && pluginModelGetter.apply(selection.iterator().next()) == null;
  }

  private static boolean isBundledUpdate(@NotNull List<?> selection, Function<Object, @Nullable PluginUiModel> pluginDescriptor) {
    return StreamEx.of(selection)
      .map(pluginDescriptor)
      .filter(Objects::nonNull)
      .allMatch(PluginUiModel::isBundledUpdate);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Collection<PluginUiModel> descriptors = getAllDescriptors();

    if (myDynamicTitle) {
      PluginUiModel uiModel = descriptors.iterator().next();
      e.getPresentation().setText(IdeBundle.message(
        descriptors.size() == 1 && uiModel.isBundledUpdate()
        ? "plugins.configurable.uninstall.bundled.update"
        : "plugins.configurable.uninstall"));
    }

    boolean disabled = descriptors.isEmpty() ||
                       exists(descriptors, PluginUiModel::isBundled) ||
                       exists(descriptors, it -> myPluginModelFacade.isUninstalled(it.getPluginId()));
    e.getPresentation().setEnabledAndVisible(!disabled);

    setShortcutSet(SHORTCUT_SET, myShowShortcut);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Map<C, PluginUiModel> selection = getSelection();

    List<PluginUiModel> toDeleteWithAsk = new ArrayList<>();
    List<PluginUiModel> toDelete = new ArrayList<>();

    List<PluginId> pluginIds = map(selection.values(), PluginUiModel::getPluginId);
    PrepareToUninstallResult prepareToUninstallResult = UiPluginManager.getInstance().prepareToUninstall(pluginIds);
    for (Map.Entry<C, PluginUiModel> entry : selection.entrySet()) {
      PluginUiModel model = entry.getValue();
      List<String> dependents = map(prepareToUninstallResult.getDependants().get(model.getPluginId()), it -> it.getName());
      if (dependents.isEmpty()) {
        toDeleteWithAsk.add(model);
      }
      else {
        boolean bundledUpdate = prepareToUninstallResult.isPluginBundled(model.getPluginId());
        if (askToUninstall(getUninstallDependentsMessage(model, dependents, bundledUpdate), entry.getKey(), bundledUpdate)) {
          toDelete.add(model);
        }
      }
    }

    boolean runFinishAction = false;

    if (!toDeleteWithAsk.isEmpty()) {
      boolean bundledUpdate = toDeleteWithAsk.size() == 1
                              && prepareToUninstallResult.isPluginBundled(toDeleteWithAsk.get(0).getPluginId());
      if (askToUninstall(getUninstallAllMessage(toDeleteWithAsk, bundledUpdate), myUiParent, bundledUpdate)) {
        for (PluginUiModel descriptor : toDeleteWithAsk) {
          myPluginModelFacade.uninstallAndUpdateUi(descriptor);
        }
        runFinishAction = true;
      }
    }

    for (PluginUiModel descriptor : toDelete) {
      myPluginModelFacade.uninstallAndUpdateUi(descriptor);
    }

    if (runFinishAction || !toDelete.isEmpty()) {
      myOnFinishAction.run();
    }
  }

  private static @NotNull
  @Nls String getUninstallAllMessage(@NotNull Collection<PluginUiModel> descriptors, boolean bundledUpdate) {
    if (descriptors.size() == 1) {
      PluginUiModel descriptor = descriptors.iterator().next();
      return IdeBundle.message("prompt.uninstall.plugin", descriptor.getName(), bundledUpdate ? 1 : 0);
    }
    return IdeBundle.message("prompt.uninstall.several.plugins", descriptors.size());
  }

  private static @NotNull @Nls String getUninstallDependentsMessage(@NotNull PluginUiModel descriptor,
                                                                    @NotNull List<String> dependents,
                                                                    boolean bundledUpdate) {
    String listOfDeps = join(dependents,
                             plugin -> "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + plugin,
                             "<br>");
    String message = IdeBundle.message("dialog.message.following.plugin.depend.on",
                                       dependents.size(),
                                       descriptor.getName(),
                                       listOfDeps,
                                       bundledUpdate ? 1 : 0);
    return XmlStringUtil.wrapInHtml(message);
  }

  private static boolean askToUninstall(@NotNull @Nls String message, @NotNull JComponent parentComponent, boolean bundledUpdate) {
    return MessageDialogBuilder.yesNo(IdeBundle.message("title.plugin.uninstall", bundledUpdate ? 1 : 0), message).ask(parentComponent);
  }
}
