// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;

public class JavaModuleType extends ModuleType<JavaModuleBuilder> {
  public static ModuleType<?> getModuleType() {
    return ModuleTypeManager.getInstance().findByID(JAVA_MODULE);
  }

  public static final String JAVA_GROUP = "Java";
  public static final String BUILD_TOOLS_GROUP = "Build Tools";
  private static final String JAVA_MODULE = ModuleTypeId.JAVA_MODULE;

  public JavaModuleType() {
    this(JAVA_MODULE);
  }

  protected JavaModuleType(@NonNls String id) {
    super(id);
  }

  @NotNull
  @Override
  public JavaModuleBuilder createModuleBuilder() {
    return new JavaModuleBuilder();
  }

  @NotNull
  @Override
  public String getName() {
    return getModuleName();
  }

  @NotNull
  @Override
  public String getDescription() {
    return JavaBundle.message("module.type.java.description");
  }

  @Override
  @NotNull
  public Icon getNodeIcon(boolean isOpened) {
    return getJavaModuleNodeIconClosed();
  }

  @Nullable
  @Override
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep, @NotNull final ModuleBuilder moduleBuilder) {
    return ProjectWizardStepFactory.getInstance().createJavaSettingsStep(settingsStep, moduleBuilder,
                                                                         moduleBuilder::isSuitableSdkType);
  }

  @NotNull
  private static Icon getJavaModuleNodeIconClosed() {
    return AllIcons.Nodes.Module;
  }

  @Override
  public boolean isValidSdk(@NotNull final Module module, final Sdk projectSdk) {
    return isValidJavaSdk(module);
  }

  public static boolean isValidJavaSdk(@NotNull Module module) {
    if (ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES).isEmpty()) return true;
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(module.getProject());
    return ReadAction.compute(() -> psiFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, module.getModuleWithLibrariesScope())) != null;
  }

  public static String getModuleName() {
    return JavaBundle.message("module.type.java.name");
  }
}
