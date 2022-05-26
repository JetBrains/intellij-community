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

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.IdeView;
import com.intellij.lang.Language;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;

public class LangDataKeys extends PlatformCoreDataKeys {

  /**
   * Returns current/selected module.
   */
  public static final DataKey<Module> MODULE_CONTEXT = DataKey.create("context.Module");
  public static final DataKey<Module[]> MODULE_CONTEXT_ARRAY = DataKey.create("context.Module.Array");

  public static final DataKey<ModifiableModuleModel> MODIFIABLE_MODULE_MODEL = DataKey.create("modifiable.module.model");

  public static final DataKey<Language[]> CONTEXT_LANGUAGES = DataKey.create("context.Languages");

  /**
   * Returns {@link IdeView} (one of project, packages, commander or favorites view).
   */
  public static final DataKey<IdeView> IDE_VIEW = DataKey.create("IDEView");

  /**
   * Allows suppressing "New..." action.
   *
   * @see com.intellij.ide.actions.NewElementAction#isEnabled(AnActionEvent)
   */
  public static final DataKey<Boolean> NO_NEW_ACTION = DataKey.create("IDEview.no.create.element.action");

  /**
   * Allows pre-selecting item in "New..." action.
   */
  public static final DataKey<Condition<AnAction>> PRESELECT_NEW_ACTION_CONDITION = DataKey.create("newElementAction.preselect.id");

  public static final DataKey<PsiElement> TARGET_PSI_ELEMENT = DataKey.create("psi.TargetElement");
  public static final DataKey<Module> TARGET_MODULE = DataKey.create("module.TargetModule");
  public static final DataKey<PsiElement> PASTE_TARGET_PSI_ELEMENT = DataKey.create("psi.pasteTargetElement");

  public static final DataKey<ConsoleView> CONSOLE_VIEW = DataKey.create("consoleView");

  public static final DataKey<JBPopup> POSITION_ADJUSTER_POPUP = DataKey.create("chooseByNameDropDown");
  public static final DataKey<JBPopup> PARENT_POPUP = DataKey.create("chooseByNamePopup");

  public static final DataKey<Library> LIBRARY = DataKey.create("project.model.library");

  public static final DataKey<RunProfile> RUN_PROFILE = DataKey.create("runProfile");
  /**
   * @deprecated Please use ExecutionDataKeys.EXECUTION_ENVIRONMENT
   */
  @Deprecated
  public static final DataKey<ExecutionEnvironment> EXECUTION_ENVIRONMENT = ExecutionDataKeys.EXECUTION_ENVIRONMENT;
  public static final DataKey<RunContentDescriptor> RUN_CONTENT_DESCRIPTOR = DataKey.create("RUN_CONTENT_DESCRIPTOR");
}
