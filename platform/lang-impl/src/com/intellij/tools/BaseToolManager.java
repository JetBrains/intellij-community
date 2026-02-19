// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.options.SchemeProcessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseToolManager<T extends Tool> implements Disposable {
  private final SchemeManagerFactory factory;
  private final SchemeManager<ToolsGroup<T>> schemeManager;

  public BaseToolManager(@NotNull SchemeManagerFactory factory, @NotNull String schemePath, @NotNull String presentableName) {
    this.factory = factory;
    //noinspection AbstractMethodCallInConstructor
    schemeManager = factory.create(schemePath,
                                   createProcessor(),
                                   presentableName,
                                   RoamingType.DEFAULT,
                                   name -> FileUtil.sanitizeFileName(name, false),
                                   null,
                                   null,
                                   true,
                                   SettingsCategory.OTHER);
    schemeManager.loadSchemes();
  }

  protected abstract SchemeProcessor<ToolsGroup<T>, ToolsGroup<T>> createProcessor();

  protected @Nullable ActionManagerEx getActionManager() {
    return ActionManagerEx.getInstanceEx();
  }

  public static @Nullable String convertString(String s) {
    return Strings.nullize(s, true);
  }

  public List<T> getTools() {
    List<T> result = new SmartList<>();
    for (ToolsGroup<T> group : schemeManager.getAllSchemes()) {
      result.addAll(group.getElements());
    }
    return result;
  }

  public @NotNull List<T> getTools(@NotNull String group) {
    ToolsGroup<T> groupByName = schemeManager.findSchemeByName(group);
    if (groupByName == null) {
      return List.of();
    }
    else {
      return groupByName.getElements();
    }
  }

  public List<ToolsGroup<T>> getGroups() {
    return schemeManager.getAllSchemes();
  }

  public void setTools(@NotNull List<ToolsGroup<T>> tools) {
    schemeManager.setSchemes(tools);
    ActionManagerEx actionManager = getActionManager();
    if (actionManager != null) {
      registerActions(actionManager.asActionRuntimeRegistrar());
    }
    CustomActionsSchema.getInstance().initActionIcons();
  }

  protected final void registerActions(@NotNull ActionRuntimeRegistrar actionRegistrar) {
    unregisterActions(actionRegistrar);

    // register
    // to prevent exception if 2 or more targets have the same name
    Set<String> registeredIds = new HashSet<>();
    for (ToolsGroup<T> group : schemeManager.getAllSchemes()) {
      String groupName = group.getName();
      if (!Strings.isEmptyOrSpaces(groupName)) {
        String groupId = getGroupIdPrefix() + groupName;
        if (registeredIds.add(groupId)) {
          ToolActionGroup<T> actionGroup = new ToolActionGroup<>(group);
          actionGroup.getTemplatePresentation().setText(group.getName(), false);
          actionGroup.getTemplatePresentation().setPopupGroup(true);
          actionRegistrar.registerAction(groupId, actionGroup);
        }
      }
      for (T tool : group.getElements()) {
        String actionId = tool.getActionId();
        if (registeredIds.add(actionId)) {
          actionRegistrar.registerAction(actionId, createToolAction(tool));
        }
      }
    }
    actionRegistrar.registerAction(getRootGroupId(), new ActionGroup() {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        ActionManager am = e == null ? ActionManager.getInstance() : e.getActionManager();
        return ContainerUtil.mapNotNull(schemeManager.getAllSchemes(), o -> {
            String groupName = o.getName();
            return Strings.isEmptyOrSpaces(groupName) ? new ToolActionGroup<>(o) : am.getAction(getGroupIdPrefix() + groupName);
          })
          .toArray(EMPTY_ARRAY);
      }
    });
  }

  protected @NotNull ToolAction createToolAction(@NotNull T tool) {
    return new ToolAction(tool);
  }

  protected abstract @NotNull String getActionIdPrefix();

  protected @NotNull String getGroupIdPrefix() {
    return getClass().getName() + "_Group_";
  }

  @ApiStatus.Internal
  public @NotNull String getRootGroupId() {
    return getClass().getName() + "_Group";
  }

  protected final void unregisterActions(@NotNull ActionRuntimeRegistrar actionManager) {
    actionManager.unregisterActionByIdPrefix(getActionIdPrefix());
    actionManager.unregisterActionByIdPrefix(getGroupIdPrefix());
    actionManager.unregisterActionByIdPrefix(getRootGroupId());
  }

  @Override
  public void dispose() {
    factory.dispose(schemeManager);
  }

  private static final class ToolActionGroup<T extends Tool> extends ActionGroup {
    final ToolsGroup<T> group;

    ToolActionGroup(@NotNull ToolsGroup<T> group) { this.group = group; }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      ActionManager am = e == null ? ActionManager.getInstance() : e.getActionManager();
      return ContainerUtil.mapNotNull(group.getElements(), o -> {
        // We used to have a bunch of IFs checking whether we want to show the given tool in the given event.getPlace().
        // But now from the UX point of view, we believe we'd better remove a bunch of checkboxes from the Edit External Tool dialog.
        // See IDEA-190856 for discussion.
        if (!o.isEnabled()) {
          return null;
        }
        return am.getAction(o.getActionId());
      }).toArray(EMPTY_ARRAY);
    }
  }
}
