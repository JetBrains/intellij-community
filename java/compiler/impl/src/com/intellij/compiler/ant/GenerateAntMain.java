package com.intellij.compiler.ant;

import com.intellij.openapi.application.ApplicationStarter;
import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class GenerateAntMain implements ApplicationStarter {
  private GenerateAntApplication myApplication;

  @NonNls
  public String getCommandName() {
    return "ant";
  }

  public void premain(String[] args) {
    System.setProperty("idea.load.plugins", "false");
    myApplication = new GenerateAntApplication();

    myApplication.myProjectPath = args[1];
    myApplication.myOutPath = args[2];    
  }

  public void main(String[] args) {
    myApplication.startup();
  }

  public static void printHelp() {
    System.out.println("Wrong params");
    System.exit(1);
  }
}
