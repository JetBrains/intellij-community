package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.WorkspaceSettings;

import java.io.File;

/**
 * @author nik
 */
public class WorkspaceSettingsImpl extends ComponentManagerSettingsImpl implements WorkspaceSettings {
  public WorkspaceSettingsImpl(File workspaceFile, ConversionContextImpl context) throws CannotConvertException {
    super(workspaceFile, context);
  }

}
