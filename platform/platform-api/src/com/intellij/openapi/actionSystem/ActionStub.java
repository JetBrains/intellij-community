// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The main (and single) purpose of this class is provide lazy initialization
 * of the actions. ClassLoader eats a lot of time on startup to load the actions' classes.
 *
 * @author Vladimir Kondratyev
 */
@SuppressWarnings("ComponentNotRegistered")
public final class ActionStub extends AnAction implements ActionStubBase {
  private static final Logger LOG = Logger.getInstance(ActionStub.class);

  private final String myClassName;
  private final String myProjectType;
  private final Supplier<Presentation> myTemplatePresentation;
  private final String myId;
  private final PluginDescriptor myPlugin;
  private final String myIconPath;
  private List<Supplier<String>> mySynonyms = Collections.emptyList();

  public ActionStub(@NotNull String actionClass,
                    @NotNull String id,
                    @NotNull PluginDescriptor plugin,
                    @Nullable String iconPath,
                    @Nullable String projectType,
                    @NotNull Supplier<Presentation> templatePresentation) {
    myPlugin = plugin;
    myClassName = actionClass;
    myProjectType = projectType;
    myTemplatePresentation = templatePresentation;
    LOG.assertTrue(!id.isEmpty());
    myId = id;
    myIconPath = iconPath;
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

  @NotNull
  @Override
  public PluginDescriptor getPlugin() {
    return myPlugin;
  }

  @NotNull
  @Override
  Presentation createTemplatePresentation() {
    return myTemplatePresentation.get();
  }

  @NotNull
  public String getClassName() {
    return myClassName;
  }

  @Override
  @NotNull
  public String getId() {
    return myId;
  }

  public ClassLoader getLoader() {
    return myPlugin.getPluginClassLoader();
  }

  @Override
  public String getIconPath() {
    return myIconPath;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    throw new UnsupportedOperationException();
  }

  /**
   * Copies template presentation and shortcuts set to {@code targetAction}.
   */
  @ApiStatus.Internal
  public final void initAction(@NotNull AnAction targetAction) {
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

  public String getProjectType() {
    return myProjectType;
  }
}
