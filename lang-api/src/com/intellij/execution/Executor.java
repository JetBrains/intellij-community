package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class Executor {
  public static final ExtensionPointName<Executor> EXECUTOR_EXTENSION_NAME = ExtensionPointName.create("com.intellij.executor");

  private Icon myIcon;
  private String myActionName;
  private String myId;

  public Executor(final Icon icon, @NonNls @NotNull final String id, @NotNull final String actionName) {
    myIcon = icon;
    myId = id;
    myActionName = actionName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public String getActionName() {
    return myActionName;
  }

  @NotNull
  @NonNls
  public String getId() {
    return myId;
  }

  public String getStartActionText() {
    return myId;
  }

}
