/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class SaveFileAsTemplateDialog extends SingleConfigurableEditor {

  public SaveFileAsTemplateDialog(@Nullable Project project,
                                  Configurable configurable) {
    super(project, configurable, "save.file.as.template.dialog");
    setTitle("Save File as Template");
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    return super.createNorthPanel();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return ArrayUtil.append(super.createActions(), getHelpAction());
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.save.file.as.template");
  }
}
