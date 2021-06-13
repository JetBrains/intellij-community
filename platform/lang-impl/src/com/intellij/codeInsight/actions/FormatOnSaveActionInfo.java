// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.application.options.GeneralCodeStylePanel;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.DropDownLink;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FormatOnSaveActionInfo extends ActionOnSaveInfo {
  private static final Key<Boolean> FORMAT_ON_SAVE_KEY = Key.create("format.on.save");
  private static final String FORMAT_ON_SAVE_PROPERTY = "format.on.save";
  private static final boolean FORMAT_ON_SAVE_DEFAULT = false;

  private static final Key<Boolean> ONLY_CHANGED_LINES_KEY = Key.create("format.on.save.only.changed.lines");
  private static final String ONLY_CHANGED_LINES_PROPERTY = "format.on.save.only.changed.lines";
  private static final boolean ONLY_CHANGED_LINES_DEFAULT = false;

  public static boolean isReformatOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(FORMAT_ON_SAVE_PROPERTY, FORMAT_ON_SAVE_DEFAULT);
  }

  public static boolean isReformatOnlyChangedLinesOnSave(@NotNull Project project) {
    return VcsFacade.getInstance().hasActiveVcss(project) &&
           PropertiesComponent.getInstance(project).getBoolean(ONLY_CHANGED_LINES_PROPERTY, ONLY_CHANGED_LINES_DEFAULT);
  }


  private static void setReformatOnSaveEnabled(@NotNull Project project, boolean enabled) {
    PropertiesComponent.getInstance(project).setValue(FORMAT_ON_SAVE_PROPERTY, enabled, FORMAT_ON_SAVE_DEFAULT);
  }

  private static void setReformatOnlyChangedLines(@NotNull Project project, boolean onlyChangedLines) {
    PropertiesComponent.getInstance(project).setValue(ONLY_CHANGED_LINES_PROPERTY, onlyChangedLines, ONLY_CHANGED_LINES_DEFAULT);
  }

  public static class FormatOnSaveActionProvider extends ActionOnSaveInfoProvider {
    @Override
    protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
      // TODO correct the supported IDE list.
      if (PlatformUtils.isIntelliJ() || PlatformUtils.isWebStorm()) {
        return List.of(new FormatOnSaveActionInfo(context));
      }
      return Collections.emptyList();
    }
  }

  public FormatOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context);
  }

  @Override
  public @NotNull String getActionOnSaveName() {
    return CodeInsightBundle.message("actions.on.save.page.checkbox.reformat.code");
  }

  @Override
  public boolean isActionOnSaveEnabled() {
    Boolean enabledInUi = getContext().getUserData(FORMAT_ON_SAVE_KEY);
    return enabledInUi != null ? enabledInUi : isReformatOnSaveEnabled(getProject());
  }

  @Override
  public void setActionOnSaveEnabled(boolean enabled) {
    getContext().putUserData(FORMAT_ON_SAVE_KEY, enabled);
  }

  @Override
  public @NotNull List<? extends ActionLink> getActionLinks() {
    return List.of(new ActionLink(CodeInsightBundle.message("actions.on.save.page.link.configure.scope"), new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        GeneralCodeStylePanel.selectFormatterTab(getSettings());
      }
    }));
  }

  @Override
  public @Nullable DropDownLink<?> getInPlaceConfigDropDownLink() {
    if (!VcsFacade.getInstance().hasActiveVcss(getProject())) return null;

    String wholeFile = CodeInsightBundle.message("actions.on.save.page.label.whole.file");
    String onlyChangedLines = CodeInsightBundle.message("actions.on.save.page.label.changed.lines");

    Boolean onlyChangedLinesFromUi = getContext().getUserData(ONLY_CHANGED_LINES_KEY);
    boolean onlyChangedLinesOnly = onlyChangedLinesFromUi != null ? onlyChangedLinesFromUi : isReformatOnlyChangedLinesOnSave(getProject());
    String current = onlyChangedLinesOnly ? onlyChangedLines : wholeFile;

    return new DropDownLink<>(current, List.of(wholeFile, onlyChangedLines),
                              choice -> getContext().putUserData(ONLY_CHANGED_LINES_KEY, choice == onlyChangedLines));
  }

  @Override
  protected void apply() {
    setReformatOnSaveEnabled(getProject(), isActionOnSaveEnabled());

    Boolean onlyChangedLinesFromUi = getContext().getUserData(ONLY_CHANGED_LINES_KEY);
    if (onlyChangedLinesFromUi != null) {
      setReformatOnlyChangedLines(getProject(), onlyChangedLinesFromUi);
    }
  }

  @Override
  protected boolean isModified() {
    if (isReformatOnSaveEnabled(getProject()) != isActionOnSaveEnabled()) return true;

    Boolean onlyChangedLinesFromUi = getContext().getUserData(ONLY_CHANGED_LINES_KEY);
    if (onlyChangedLinesFromUi != null && isReformatOnlyChangedLinesOnSave(getProject()) != onlyChangedLinesFromUi) return true;

    return false;
  }
}
