/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 23, 2002
 * Time: 5:20:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.openapi.application.ApplicationStarter;

import java.util.Arrays;

public class InspectionMain implements ApplicationStarter {
  private InspectionApplication myApplication;

  @Override
  public String getCommandName() {
    return "inspect";
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void premain(String[] args) {
    if (args.length < 4) {
      System.err.println("invalid args:" + Arrays.toString(args));
      printHelp();
    }

    //System.setProperty("idea.load.plugins.category", "inspection");
    myApplication = new InspectionApplication();

    myApplication.myHelpProvider = new InspectionToolCmdlineOptionHelpProvider() {
      @Override
      public void printHelpAndExit() {
        printHelp();
      }
    };
    myApplication.myProjectPath = args[1];
    myApplication.myStubProfile = args[2];
    myApplication.myOutPath = args[3];

    if (myApplication.myProjectPath == null
        || myApplication.myOutPath == null
        || myApplication.myStubProfile == null) {
      System.err.println(myApplication.myProjectPath + myApplication.myOutPath + myApplication.myStubProfile);
      printHelp();
    }


    try {
      for (int i = 4; i < args.length; i++) {
        String arg = args[i];
        if ("-profileName".equals(arg)) {
          myApplication.myProfileName = args[++i];
        } else if ("-profilePath".equals(arg)) {
          myApplication.myProfilePath = args[++i];
        } else if ("-d".equals(arg)) {
          myApplication.mySourceDirectory = args[++i];
        }
        else if ("-v0".equals(arg)) {
          myApplication.setVerboseLevel(0);
        }
        else if ("-v1".equals(arg)) {
          myApplication.setVerboseLevel(1);
        }
        else if ("-v2".equals(arg)) {
          myApplication.setVerboseLevel(2);
        }
        else if ("-v3".equals(arg)) {
          myApplication.setVerboseLevel(3);
        }
        else if ("-e".equals(arg)){
          myApplication.myRunWithEditorSettings = true;
        }
        else if ("-t".equals(arg)) {
          myApplication.myErrorCodeRequired = false;
        }
        else {
          System.err.println("unexpected argument: " + arg);
          printHelp();
        }
      }
    }
    catch (ArrayIndexOutOfBoundsException e) {
      e.printStackTrace();
      printHelp();
    }

    myApplication.myRunGlobalToolsOnly = System.getProperty("idea.no.local.inspections") != null;
  }

  @Override
  public void main(String[] args) {
    myApplication.startup();
  }

  public static void printHelp() {
    System.out.println(InspectionsBundle.message("inspection.command.line.explanation"));
    System.exit(1);
  }
}

