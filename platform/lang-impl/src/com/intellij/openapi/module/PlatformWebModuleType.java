package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PlatformWebModuleType extends ModuleType<EmptyModuleBuilder> {
  @NonNls public static final String WEB_MODULE = "WEB_MODULE";

  public PlatformWebModuleType() {
    super(WEB_MODULE);
  }

  @NotNull
  public static PlatformWebModuleType getInstance() {
    return (PlatformWebModuleType)ModuleTypeManager.getInstance().findByID(WEB_MODULE);
  }

  public EmptyModuleBuilder createModuleBuilder() {
    return new EmptyModuleBuilder() {
      @Override
      public ModuleType getModuleType() {
        return getInstance();
      }
    };
  }

  public String getName() {
    return ProjectBundle.message("module.web.title");
  }

  public String getDescription() {
    return ProjectBundle.message("module.web.description");
  }

  public Icon getBigIcon() {
    return IconLoader.getIcon(((ApplicationInfoEx)ApplicationInfo.getInstance()).getSmallIconUrl());
  }

  public Icon getNodeIcon(boolean isOpened) {
    return getBigIcon();
  }
}