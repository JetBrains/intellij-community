package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkGroup;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FrameworkGroupNode extends FrameworkSupportNodeBase<FrameworkGroup> {

  public FrameworkGroupNode(@NotNull FrameworkGroup<?> group, FrameworkSupportNodeBase parent) {
    super(group, parent);
  }
}
