// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.application.ApplicationStarter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public class InspectionMain implements ApplicationStarter {
  private InspectionApplicationBase myApplication;

  @Override
  public String getCommandName() {
    return "inspect";
  }

  @Override
  public int getRequiredModality() {
    return NOT_IN_EDT;
  }

  @Override
  public void premain(@NotNull List<String> args) {
    InspectionApplication.LOG.info("Command line arguments: " + args);
    if (args.size() > 1 && "qodana".equals(args.get(1))) {
      try {
        myApplication = InspectionApplicationFactory.getApplication("qodana", args.subList(2, args.size()));
      }
      catch (Exception e) {
        e.printStackTrace(); // workaround for IDEA-289086
        System.exit(1);
      }
      return;
    }
    myApplication = new InspectionApplication();
    if (args.size() < 4) {
      System.err.println("invalid args:" + args);
      printHelp();
    }

    myApplication.myHelpProvider = () -> printHelp();
    myApplication.myProjectPath = args.get(1);
    myApplication.myStubProfile = args.get(2);
    myApplication.myOutPath = args.get(3);

    if (myApplication.myProjectPath == null
        || myApplication.myOutPath == null
        || myApplication.myStubProfile == null) {
      System.err.println(myApplication.myProjectPath + myApplication.myOutPath + myApplication.myStubProfile);
      printHelp();
    }

    try {
      for (int i = 4; i < args.size(); i++) {
        String arg = args.get(i);
        if ("-profileName".equals(arg)) {
          myApplication.myProfileName = args.get(++i);
        }
        else if ("-profilePath".equals(arg)) {
          myApplication.myProfilePath = args.get(++i);
        }
        else if ("-d".equals(arg)) {
          myApplication.mySourceDirectory = args.get(++i);
        }
        else if ("-scope".equals(arg)) {
          myApplication.myScopePattern = args.get(++i);
        }
        else if ("-targets".equals(arg)) {
          myApplication.myTargets = args.get(++i);
        }
        else if ("-format".equals(arg)) {
          myApplication.myOutputFormat = args.get(++i);
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
        else if ("-e".equals(arg)) {
          myApplication.myRunWithEditorSettings = true;
        }
        else if ("-t".equals(arg)) {
          myApplication.myErrorCodeRequired = false;
        }
        else if ("-changes".equals(arg)) {
          myApplication.myAnalyzeChanges = true;
        }
        else //noinspection StatementWithEmptyBody
          if ("-qodana".equals(arg)) {
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
  public void main(String @NotNull [] args) {
    myApplication.startup();
  }

  private static void printHelp() {
    System.out.println(InspectionsBundle.message("inspection.command.line.explanation"));
    System.exit(1);
  }
}
