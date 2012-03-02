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

package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public abstract class Executor {
  public static final ExtensionPointName<Executor> EXECUTOR_EXTENSION_NAME = ExtensionPointName.create("com.intellij.executor");

  public abstract String getToolWindowId();
  public abstract Icon getToolWindowIcon();

  @NotNull
  public abstract Icon getIcon();
  public abstract Icon getDisabledIcon();

  public abstract String getDescription();

  @NotNull
  public abstract String getActionName();

  @NotNull
  @NonNls
  public abstract String getId();

  @NotNull
  public abstract String getStartActionText();

  @NonNls
  public abstract String getContextActionId();

  @NonNls
  public abstract String getHelpId();

  public String getStartActionText(String configurationName) {
    return getStartActionText() + " '" + StringUtil.first(configurationName, 30, true) + "'";
  }
}
