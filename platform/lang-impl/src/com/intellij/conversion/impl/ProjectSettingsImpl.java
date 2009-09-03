package com.intellij.conversion.impl;

import com.intellij.conversion.ProjectSettings;
import com.intellij.conversion.CannotConvertException;

import java.io.File;

/**
 * @author nik
 */
public class ProjectSettingsImpl extends ComponentManagerSettingsImpl implements ProjectSettings {
  public ProjectSettingsImpl(File file, ConversionContextImpl context) throws CannotConvertException {
    super(file, context);
  }
}
