package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class WebModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> {
  @NonNls public static final String WEB_MODULE = "WEB_MODULE";

  public WebModuleTypeBase() {
    super(WEB_MODULE);
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
