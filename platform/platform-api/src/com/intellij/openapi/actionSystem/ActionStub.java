// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

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
  private final IdeaPluginDescriptor myPlugin;
  private final String myIconPath;

  public ActionStub(@NotNull String actionClass,
                    @NotNull String id,
                    @NotNull IdeaPluginDescriptor plugin,
                    String iconPath,
                    String projectType,
                    @NotNull Supplier<Presentation> templatePresentation) {
    myPlugin = plugin;
    myClassName = actionClass;
    myProjectType = projectType;
    myTemplatePresentation = templatePresentation;
    LOG.assertTrue(!id.isEmpty());
    myId = id;
    myIconPath = iconPath;
  }

  @NotNull
  @Override
  public IdeaPluginDescriptor getPlugin() {
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
  public PluginId getPluginId() {
    return myPlugin.getPluginId();
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
  public final void initAction(@NotNull AnAction targetAction) {
    copyTemplatePresentation(this.getTemplatePresentation(), targetAction.getTemplatePresentation());
    targetAction.setShortcutSet(getShortcutSet());
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
