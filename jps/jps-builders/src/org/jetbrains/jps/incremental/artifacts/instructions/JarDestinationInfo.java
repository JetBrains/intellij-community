// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class JarDestinationInfo extends DestinationInfo {
  private static final Logger LOG = Logger.getInstance(JarDestinationInfo.class);
  private final String myPathInJar;
  private final JarInfo myJarInfo;

  public JarDestinationInfo(final String pathInJar, final JarInfo jarInfo, DestinationInfo jarDestination) {
    super(appendPathInJar(jarDestination.getOutputPath(), pathInJar), jarDestination.getOutputFilePath());
    LOG.assertTrue(!pathInJar.startsWith(".."), pathInJar);
    myPathInJar = StringUtil.startsWithChar(pathInJar, '/') ? pathInJar : "/" + pathInJar;
    myJarInfo = jarInfo;
  }

  private static String appendPathInJar(String outputPath, String pathInJar) {
    LOG.assertTrue(!outputPath.isEmpty() && outputPath.charAt(outputPath.length() - 1) != '/');
    return outputPath + "!/" + pathInJar;
  }

  public String getPathInJar() {
    return myPathInJar;
  }

  public JarInfo getJarInfo() {
    return myJarInfo;
  }

  @Override
  public String toString() {
    return myPathInJar + "(" + getOutputPath() + ")";
  }
}
