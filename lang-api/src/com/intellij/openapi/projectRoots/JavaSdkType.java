/*
 * User: anna
 * Date: 14-Jan-2008
 */
package com.intellij.openapi.projectRoots;

import org.jetbrains.annotations.NonNls;

public interface JavaSdkType {
  @NonNls
  String getBinPath(Sdk sdk);

  @NonNls
  String getToolsPath(Sdk sdk);

  @NonNls
  String getVMExecutablePath(Sdk sdk);
}