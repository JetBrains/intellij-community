/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.compiler.ant;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.compiler.actions.GenerateAntBuildAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class GenerateAntApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionApplication");

  public String myProjectPath = null;
  public String myOutPath = null;
  private Project myProject;
  private int myVerboseLevel = 0;

  public void startup() {
    if (myProjectPath == null || myOutPath == null) {
      GenerateAntMain.printHelp();
    }

    SwingUtilities.invokeLater(() -> {
      ApplicationEx application = ApplicationManagerEx.getApplicationEx();
      try {
        logMessage(0, "Starting app... ");
        application.doNotSave();
        application.load();
        logMessageLn(0, "done");

        this.run();
      }
      catch (Exception e) {
        GenerateAntApplication.LOG.error(e);
      }
      finally {
        application.exit(true, true);
      }
    });
  }

  public void run() {
    try {
      myProjectPath = myProjectPath.replace(File.separatorChar, '/');
      VirtualFile vfsProject = LocalFileSystem.getInstance().findFileByPath(myProjectPath);
      if (vfsProject == null) {
        logError(InspectionsBundle.message("inspection.application.file.cannot.be.found", myProjectPath));
        GenerateAntMain.printHelp();
      }

      logMessage(0, "Loading project...");
      myProject = ProjectManagerEx.getInstanceEx().loadProject(myProjectPath);

      logMessageLn(0, " done");

      GenerateAntBuildAction.generateSingleFileBuild(myProject,
                                                     new GenerationOptionsImpl(myProject, true, true, false, false, new String[] {}),
                                                     new File("/Users/max/build/build.xml"),
                                                     new File("/Users/max/build/build.properties"));

      logMessage(0, "Hello!");
    }
    catch (IOException e) {
      GenerateAntApplication.LOG.error(e);
      GenerateAntMain.printHelp();
    }
    catch (Exception e) {
      GenerateAntApplication.LOG.error(e);
      System.exit(1);
    }
  }

  public void setVerboseLevel(int verboseLevel) {
    myVerboseLevel = verboseLevel;
  }

  private void logMessage(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.print(message);
    }
  }

  private void logError(String message) {
    System.err.println(message);
  }

  private void logMessageLn(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.println(message);
    }
  }
}
