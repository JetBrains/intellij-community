package com.intellij.execution.impl;

/**
 * User: anna
 * Date: Mar 14, 2005
 */
public interface CheckableRunConfigurationEditor<Settings> {
  //override this method to provide light check
  // for your run configuration data in editor for warnings
  void checkEditorData(Settings s);
}
