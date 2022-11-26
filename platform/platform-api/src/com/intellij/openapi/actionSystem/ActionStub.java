// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.util.text.StringUtil;
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

  private final @NotNull String myClassName;
  private final @NotNull String myId;
  private final @NotNull PluginDescriptor myPlugin;
  private final @Nullable String myIconPath;
  private final @Nullable ProjectType myProjectType;
  private final @NotNull Supplier<? extends Presentation> myTemplatePresentation;
  private List<Supplier<String>> mySynonyms = Collections.emptyList();

  public ActionStub(@NotNull String actionClass,
                    @NotNull String id,
                    @NotNull PluginDescriptor plugin,
                    @Nullable String iconPath,
                    @Nullable ProjectType projectType,
                    @NotNull Supplier<? extends Presentation> templatePresentation) {
    myClassName = actionClass;
    LOG.assertTrue(!id.isEmpty());
    myId = id;
    myPlugin = plugin;
    myIconPath = iconPath;
    myProjectType = projectType;
    myTemplatePresentation = templatePresentation;
  }

  @Override
  public void addSynonym(@NotNull Supplier<String> text) {
    if (mySynonyms == Collections.<Supplier<String>>emptyList()) {
      mySynonyms = new SmartList<>(text);
    }
    else {
      mySynonyms.add(text);
    }
  }

  @Override
  public @NotNull PluginDescriptor getPlugin() {
    return myPlugin;
  }

  @Override
  @NotNull Presentation createTemplatePresentation() {
    return myTemplatePresentation.get();
  }

  public @NotNull String getClassName() {
    return myClassName;
  }

  @Override
  public @NotNull String getId() {
    return myId;
  }

  @Override
  public @Nullable String getIconPath() {
    return myIconPath;
  }

  public @Nullable ProjectType getProjectType() {
    return myProjectType;
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
    copyTemplatePresentation(this.getTemplatePresentation(), targetAction.getTemplatePresentation());
    targetAction.setShortcutSet(getShortcutSet());
    copyActionTextOverrides(targetAction);
    for (Supplier<String> synonym : mySynonyms) {
      targetAction.addSynonym(synonym);
    }
  }

  public static void copyTemplatePresentation(Presentation sourcePresentation, Presentation targetPresentation) {
    if (targetPresentation.getIcon() == null && sourcePresentation.getIcon() != null) {
      targetPresentation.setIcon(sourcePresentation.getIcon());
    }
    if (StringUtil.isEmpty(targetPresentation.getText()) && sourcePresentation.getText() != null) {
      targetPresentation.setTextWithMnemonic(sourcePresentation.getTextWithPossibleMnemonic());
    }
    if (targetPresentation.getDescription() == null && sourcePresentation.getDescription() != null) {
      targetPresentation.setDescription(sourcePresentation.getDescription());
    }
  }
}
