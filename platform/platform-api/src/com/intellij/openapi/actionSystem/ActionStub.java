// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The main (and single) purpose of this class is to provide lazy initialization
 * of the actions.
 * ClassLoader eats up a lot of time on startup to load the actions' classes.
 */
public final class ActionStub extends AnAction implements ActionStubBase {
  private static final Logger LOG = Logger.getInstance(ActionStub.class);

  private final @NotNull String className;
  private final @NotNull String id;
  private final @NotNull PluginDescriptor plugin;
  private final @Nullable String iconPath;
  private final @Nullable ProjectType projectType;
  private final @NotNull Supplier<Presentation> templatePresentation;
  private List<Supplier<String>> synonyms = Collections.emptyList();

  public ActionStub(@NotNull String actionClass,
                    @NotNull String id,
                    @NotNull PluginDescriptor plugin,
                    @Nullable String iconPath,
                    @Nullable ProjectType projectType,
                    @NotNull Supplier<Presentation> templatePresentation) {
    className = actionClass;
    LOG.assertTrue(!id.isEmpty());
    this.id = id;
    this.plugin = plugin;
    this.iconPath = iconPath;
    this.projectType = projectType;
    this.templatePresentation = templatePresentation;
  }

  @Override
  public void addSynonym(@NotNull Supplier<String> text) {
    if (synonyms == Collections.<Supplier<String>>emptyList()) {
      synonyms = new SmartList<>(text);
    }
    else {
      synonyms.add(text);
    }
  }

  @Override
  public @NotNull PluginDescriptor getPlugin() {
    return plugin;
  }

  @Override
  @NotNull Presentation createTemplatePresentation() {
    return templatePresentation.get();
  }

  public @NotNull String getClassName() {
    return className;
  }

  @Override
  public @NotNull String getId() {
    return id;
  }

  @Override
  public @Nullable String getIconPath() {
    return iconPath;
  }

  public @Nullable ProjectType getProjectType() {
    return projectType;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    throw new UnsupportedOperationException();
  }

  /**
   * Copies template presentation and shortcuts set to {@code targetAction}.
   */
  @ApiStatus.Internal
  public void initAction(@NotNull AnAction targetAction) {
    copyTemplatePresentation(getTemplatePresentation(), targetAction.getTemplatePresentation());
    targetAction.setShortcutSet(getShortcutSet());
    copyActionTextOverrides(targetAction);
    for (Supplier<String> synonym : synonyms) {
      targetAction.addSynonym(synonym);
    }
    if (targetAction instanceof ActionGroup) {
      LOG.warn(String.format("ActionGroup should be registered using <group> tag: id=\"%s\" class=\"%s\"",
                             id, targetAction.getClass().getName()));
    }
  }

  public static void copyTemplatePresentation(Presentation sourcePresentation, Presentation targetPresentation) {
    targetPresentation.copyIconIfUnset(sourcePresentation);
    if (Strings.isEmpty(targetPresentation.getText()) && sourcePresentation.getText() != null) {
      targetPresentation.setTextWithMnemonic(sourcePresentation.getTextWithPossibleMnemonic());
    }
    if (targetPresentation.getDescription() == null && sourcePresentation.getDescription() != null) {
      targetPresentation.setDescription(sourcePresentation.getDescription());
    }
  }
}
