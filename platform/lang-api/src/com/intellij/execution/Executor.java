package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
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
}
