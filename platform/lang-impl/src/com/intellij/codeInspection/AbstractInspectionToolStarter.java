package com.intellij.codeInspection;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationStarter;
import com.sampullara.cli.Args;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public abstract class AbstractInspectionToolStarter implements ApplicationStarter {
  protected InspectionApplication myApplication;
  protected InspectionToolCmdlineOptions myOptions;

  protected abstract AbstractInspectionCmdlineOptions createCmdlineOptions();

  @Override
  public void premain(String[] args) {
    myOptions = createCmdlineOptions();
    try {
      Args.parse(myOptions, args);
    } catch (Exception e) {
      printHelpAndExit(args, myOptions);
      return;
    }

    if (verbose(myOptions)) {
      final StringBuilder buff = new StringBuilder("Options:");
      printArgs(args, buff);
      buff.append("\n");
      System.out.println(buff.toString());
    }

    // TODO[romeo] : if config given - parse config and set attrs
       //Properties p = new Properties();
       // p.put("input", "inputfile");
       // p.put("o", "outputfile");
       // p.put("someflag", "true");
       // p.put("m", "10");
       // p.put("values", "1:2:3");
       // p.put("strings", "sam;dave;jolly");
       // PropertiesArgs.parse(tc, p);
    try{
      myOptions.validate();
    } catch (InspectionToolCmdlineOptions.CmdlineArgsValidationException e) {
      System.err.println(e.getMessage());
      printHelpAndExit(args, myOptions);
      return;
    }

    myApplication = new InspectionApplication();
    initApplication(myApplication, myOptions);

    if (storeSettingsInTmpDir()) {
      // use tmp dirs for settings
      //PathManager.useTmpSettings();
    }
  }

  /**
   * Override to change default behaviour.
   * @return true if all application caches, settings, etc should be stored in tmp folder.
   */
  protected boolean storeSettingsInTmpDir() {
    return true;
  }

  protected InspectionApplication getApplication() {
    return myApplication;
  }

  @Override
  public void main(String[] args) {
    myOptions.beforeStartup();
    myApplication.startup();
  }

  private void initApplication(@NotNull final InspectionApplication application,
                               @NotNull final InspectionToolCmdlineOptions opts) {
    opts.initApplication(application);
  }

  private boolean verbose(final InspectionToolCmdlineOptions opts) {
    return opts.getVerboseLevelProperty() > 0;
  }

  protected void printArgs(String[] args, StringBuilder buff) {
    if (args.length < 2) {
      buff.append(" no arguments");
    } else {
      for (int i = 1, argsLength = args.length; i < argsLength; i++) {
        String arg = args[i];
        buff.append(' ').append(GeneralCommandLine.quote(arg));
      }
    }
  }

  protected void printHelpAndExit(final String[] args, final InspectionToolCmdlineOptions opts) {
    final StringBuilder buff = new StringBuilder();
    buff.append("\n");
    buff.append("Invalid options or syntax:");
    printArgs(args, buff);
    System.err.println(buff.toString());
    opts.printHelpAndExit();
  }
}
