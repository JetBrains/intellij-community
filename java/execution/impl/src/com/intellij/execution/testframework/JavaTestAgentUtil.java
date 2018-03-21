// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class JavaTestAgentUtil {
  private static final Logger LOG = Logger.getInstance(JavaTestAgentUtil.class);
  public static final String JAVA_TEST_AGENT_AGENT_PATH = "java.test.agent.lib.path";

  @NotNull
  public static String handleSpacesInAgentPath(@NotNull String agentPath) {
    return FileUtil.join(handleSpacesInContainingDir(agentPath), new File(agentPath).getName());
  }

  private static String handleSpacesInContainingDir(String agentPath) {
    String agentContainingDir;
    String userDefined = System.getProperty(JAVA_TEST_AGENT_AGENT_PATH);
    if (userDefined != null && new File(userDefined).exists()) {
      agentContainingDir = userDefined;
    } else {
      agentContainingDir = new File(agentPath).getParent();
    }
    if (!SystemInfo.isWindows && agentContainingDir.contains(" ")) {
      File dir = new File(PathManager.getSystemPath(), "testAgentJars");
      if (dir.getAbsolutePath().contains(" ")) {
        try {
          dir = FileUtil.createTempDirectory("testAgent", "jars");
          if (dir.getAbsolutePath().contains(" ")) {
            LOG.info("Java test agent not used since the agent path contains spaces: " + agentContainingDir + "\n" +
                     "One can move the agent libraries to a directory with no spaces in path and specify its path in idea.properties as " +
                     JAVA_TEST_AGENT_AGENT_PATH + "=<path>");
            return agentContainingDir;
          }
        }
        catch (IOException e) {
          LOG.info(e);
          return agentContainingDir;
        }
      }

      try {
        LOG.info("Agent jars were copied to " + dir.getPath());
        FileUtil.copyDir(new File(agentContainingDir), dir, pathname -> FileUtilRt.extensionEquals(pathname.getPath(), "jar"));
        return dir.getPath();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return agentContainingDir;
  }
}
