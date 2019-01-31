/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem;

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
public class ActionStub extends AnAction{
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.actionSystem.ActionStub");

  private final String myClassName;
  private final String myProjectType;
  private final Supplier<Presentation> myTemplatePresentation;
  private final String myId;
  private final ClassLoader myLoader;
  private final PluginId myPluginId;
  private final String myIconPath;

  public ActionStub(@NotNull String actionClass,
                    @NotNull String id,
                    ClassLoader loader,
                    PluginId pluginId,
                    String iconPath, String projectType,
                    @NotNull Supplier<Presentation> templatePresentation) {
    myLoader = loader;
    myClassName=actionClass;
    myProjectType = projectType;
    myTemplatePresentation = templatePresentation;
    LOG.assertTrue(!id.isEmpty());
    myId=id;
    myPluginId = pluginId;
    myIconPath = iconPath;
  }

  @NotNull
  @Override
  Presentation createTemplatePresentation() {
    return myTemplatePresentation.get();
  }

  @NotNull
  public String getClassName(){
    return myClassName;
  }

  @NotNull
  public String getId(){
    return myId;
  }

  public ClassLoader getLoader() {
    return myLoader;
  }

  public PluginId getPluginId() {
    return myPluginId;
  }

  public String getIconPath() {
    return myIconPath;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e){
    throw new UnsupportedOperationException();
  }

  /**
   * Copies template presentation and shortcuts set to {@code targetAction}.
   *
   * @param targetAction cannot be {@code null}
   */
  public final void initAction(@NotNull AnAction targetAction) {
    Presentation sourcePresentation = getTemplatePresentation();
    Presentation targetPresentation = targetAction.getTemplatePresentation();
    if (targetPresentation.getIcon() == null && sourcePresentation.getIcon() != null) {
      targetPresentation.setIcon(sourcePresentation.getIcon());
    }
    if (StringUtil.isEmpty(targetPresentation.getText()) && sourcePresentation.getText() != null) {
      targetPresentation.setText(sourcePresentation.getTextWithMnemonic(), true);
    }
    if (targetPresentation.getDescription() == null && sourcePresentation.getDescription() != null) {
      targetPresentation.setDescription(sourcePresentation.getDescription());
    }
    targetAction.setShortcutSet(getShortcutSet());
    targetAction.markAsGlobal();
  }

  public String getProjectType() {
    return myProjectType;
  }
}
