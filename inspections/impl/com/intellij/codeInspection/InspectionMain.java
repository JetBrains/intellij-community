/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 23, 2002
 * Time: 5:20:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.InspectionApplication;
import com.intellij.openapi.application.ApplicationStarter;

public class InspectionMain implements ApplicationStarter {
  private InspectionApplication myApplication;

  public String getCommandName() {
    return "inspect";
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void premain(String[] args) {
    if (args.length < 4) {
      System.err.println("invalid args:" + args);
      printHelp();
    }

    System.setProperty("idea.load.plugins.category", "inspection");
    myApplication = new InspectionApplication();

    myApplication.myProjectPath = args[1];
    myApplication.myProfileName = args[2];
    myApplication.myOutPath = args[3];

    try {
      for (int i = 4; i < args.length; i++) {
        String arg = args[i];
        if ("-d".equals(arg)) {
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

    myApplication.myRunGlobalToolsOnly = System.getProperty("idea.global.inspections") != null;
  }

  public void main(String[] args) {
    myApplication.startup();
  }

  public static void printHelp() {
    System.out.println(InspectionsBundle.message("inspection.command.line.explanation"));
    System.exit(1);
  }
}

