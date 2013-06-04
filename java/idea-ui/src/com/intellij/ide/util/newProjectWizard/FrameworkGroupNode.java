package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class FrameworkGroupNode extends FrameworkSupportNodeBase {
  private final FrameworkGroup<?> myGroup;

  public FrameworkGroupNode(@NotNull FrameworkGroup<?> group, FrameworkSupportNodeBase parent) {
    super(group, parent);
    myGroup = group;
  }

  public FrameworkGroup<?> getGroup() {
    return myGroup;
  }

  @NotNull
  @Override
  protected String getTitle() {
    return myGroup.getPresentableName();
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myGroup.getIcon();
  }

  @NotNull
  @Override
  public String getId() {
    return myGroup.getId();
  }
}
