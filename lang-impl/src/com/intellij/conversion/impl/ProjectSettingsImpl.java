package com.intellij.conversion.impl;

import com.intellij.conversion.ProjectSettings;
import com.intellij.ide.impl.convert.QualifiedJDomException;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class ProjectSettingsImpl extends ComponentManagerSettingsImpl implements ProjectSettings {
  public ProjectSettingsImpl(File file) throws IOException, QualifiedJDomException {
    super(file);
  }
}
