/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author nik
 */
public class JarDestinationInfo extends DestinationInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.packagingCompiler.JarDestinationInfo");
  private final String myPathInJar;
  private final JarInfo myJarInfo;

  public JarDestinationInfo(final String pathInJar, final JarInfo jarInfo, DestinationInfo jarDestination) {
    super(DeploymentUtil.appendToPath(jarDestination.getOutputPath(), pathInJar), jarDestination.getOutputFile(), jarDestination.getOutputFilePath());
    LOG.assertTrue(!pathInJar.startsWith(".."), pathInJar);
    myPathInJar = StringUtil.startsWithChar(pathInJar, '/') ? pathInJar : "/" + pathInJar;
    myJarInfo = jarInfo;
  }

  public String getPathInJar() {
    return myPathInJar;
  }

  public JarInfo getJarInfo() {
    return myJarInfo;
  }

  public String toString() {
    return myPathInJar + "(" + getOutputPath() + ")";
  }
}
