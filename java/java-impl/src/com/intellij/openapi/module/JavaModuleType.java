// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;

import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;

public class JavaModuleType extends ModuleType<JavaModuleBuilder> {
  public static ModuleType<?> getModuleType() {
    return ModuleTypeManager.getInstance().findByID(JAVA_MODULE_ENTITY_TYPE_ID_NAME);
  }

  public static final String JAVA_GROUP = "Java";
  public static final String BUILD_TOOLS_GROUP = "Build Tools";

  public JavaModuleType() {
    this(JAVA_MODULE_ENTITY_TYPE_ID_NAME);
  }

  protected JavaModuleType(@NonNls String id) {
    super(id);
  }

  @Override
  public @NotNull JavaModuleBuilder createModuleBuilder() {
    return new JavaModuleBuilder();
  }

  @Override
  public @NotNull String getName() {
    return getModuleName();
  }

  @Override
  public @NotNull String getDescription() {
    return JavaBundle.message("module.type.java.description");
  }

  @Override
  public @NotNull Icon getNodeIcon(boolean isOpened) {
    return getJavaModuleNodeIconClosed();
  }

  @Override
  public @Nullable ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep, final @NotNull ModuleBuilder moduleBuilder) {
    return ProjectWizardStepFactory.getInstance().createJavaSettingsStep(settingsStep, moduleBuilder,
                                                                         moduleBuilder::isSuitableSdkType);
  }

  private static @NotNull Icon getJavaModuleNodeIconClosed() {
    return AllIcons.Nodes.Module;
  }

  @Override
  public boolean isValidSdk(final @NotNull Module module, final Sdk projectSdk) {
    return isValidJavaSdk(module);
  }

  public static boolean isValidJavaSdk(@NotNull Module module) {
    if (ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES).isEmpty()) return true;
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(module.getProject());
    return ReadAction.compute(() -> psiFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, module.getModuleWithLibrariesScope())) != null;
  }

  public static @Nls String getModuleName() {
    return JavaBundle.message("module.type.java.name");
  }
}
