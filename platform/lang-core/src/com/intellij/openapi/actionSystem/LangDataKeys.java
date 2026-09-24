
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.IdeView;
import com.intellij.lang.Language;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;

/**
 * @see PlatformCoreDataKeys
 * @see CommonDataKeys
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys
 */
public class LangDataKeys extends PlatformCoreDataKeys {

  /**
   * Returns current/selected module.
   * @see #MODULE_CONTEXT_ARRAY
   */
  public static final DataKey<Module> MODULE_CONTEXT = DataKey.create("context.Module");

  /**
   * @see #MODULE_CONTEXT
   */
  public static final DataKey<Module[]> MODULE_CONTEXT_ARRAY = DataKey.create("context.Module.Array");

  public static final DataKey<ModifiableModuleModel> MODIFIABLE_MODULE_MODEL = DataKey.create("modifiable.module.model");

  public static final DataKey<Language[]> CONTEXT_LANGUAGES = DataKey.create("context.Languages");

  /**
   * Returns {@link IdeView} (one of project, packages, commander or favorites view).
   */
  public static final DataKey<IdeView> IDE_VIEW = DataKey.create(Names.IDE_VIEW_KEY_NAME);

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

  /**
   * @deprecated use {@link ExecutionDataKeys#CONSOLE_VIEW} or cast to {@code DataKey<ConsoleView>} explicitly
   */
  @Deprecated
  public static final DataKey<?> CONSOLE_VIEW = DataKey.create("consoleView");

  public static final DataKey<JBPopup> POSITION_ADJUSTER_POPUP = DataKey.create("chooseByNameDropDown");
  public static final DataKey<JBPopup> PARENT_POPUP = DataKey.create("chooseByNamePopup");

  public static final DataKey<Library> LIBRARY = DataKey.create("project.model.library");

  /**
   * @deprecated use {@link ExecutionDataKeys#RUN_PROFILE} or cast to {@code DataKey<RunProfile>} explicitly
   */
  @Deprecated
  public static final DataKey<?> RUN_PROFILE = DataKey.create("runProfile");

  /**
   * @deprecated use {@link ExecutionDataKeys#EXECUTION_ENVIRONMENT}
   */
  @Deprecated
  public static final DataKey<?> EXECUTION_ENVIRONMENT = DataKey.create("executionEnvironment");

  /**
   * @deprecated use {@link ExecutionDataKeys#RUN_CONTENT_DESCRIPTOR} or cast to {@code DataKey<RunContentDescriptor>} explicitly
   */
  @Deprecated
  public static final DataKey<?> RUN_CONTENT_DESCRIPTOR = DataKey.create("RUN_CONTENT_DESCRIPTOR");

  @ApiStatus.Internal
  public interface Names {
    String IDE_VIEW_KEY_NAME = "IDEView";
  }
}
