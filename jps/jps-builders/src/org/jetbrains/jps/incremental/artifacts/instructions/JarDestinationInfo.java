/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author nik
 */
public class JarDestinationInfo extends DestinationInfo {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.artifacts.instructions.JarDestinationInfo");
  private final String myPathInJar;
  private final JarInfo myJarInfo;

  public JarDestinationInfo(final String pathInJar, final JarInfo jarInfo, DestinationInfo jarDestination) {
    super(appendPathInJar(jarDestination.getOutputPath(), pathInJar), jarDestination.getOutputFilePath());
    LOG.assertTrue(!pathInJar.startsWith(".."), pathInJar);
    myPathInJar = StringUtil.startsWithChar(pathInJar, '/') ? pathInJar : "/" + pathInJar;
    myJarInfo = jarInfo;
  }

  private static String appendPathInJar(String outputPath, String pathInJar) {
    LOG.assertTrue(outputPath.length() > 0 && outputPath.charAt(outputPath.length() - 1) != '/');
    return outputPath + "!/" + pathInJar;
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
