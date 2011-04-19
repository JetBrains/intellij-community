package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.PlainTextFormatter;
import com.sampullara.cli.Args;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Roman.Chernyatchik
 */
public abstract class AbstractInspectionCmdlineOptions implements InspectionToolCmdlineOptions {
  protected abstract String getProfileNameOrPath();

  protected abstract String getProjectPath();
  @Nullable
  protected abstract String getOutputPath();
  protected abstract String getDirToInspect();
  @Nullable
  protected abstract String getOutputFormat();
  @Nullable
  protected abstract String getXSLTSchemePath();
  @Nullable
  protected abstract Boolean getErrorCodeRequired();
  @Nullable
  protected abstract Boolean getRunWithEditorSettings();


  public void initApplication(InspectionApplication app) {
    app.myHelpProvider = this;
    app.myProjectPath = determineProjectPath();
    app.myProfileName = getProfileNameOrPath();
    // if plain formatter and output path not specified - use STDOUT
    // otherwise specified output path or a default one
    app.myOutPath = getOutputPath() != null ? getOutputPath()
                                            : getOutputFormat() == PlainTextFormatter.NAME ? null
                                                                                           : getDefaultOutputPath();
    app.mySourceDirectory = getDirToInspect();
    app.setVerboseLevel(getVerboseLevel());

    final Boolean errorCodeRequired = getErrorCodeRequired();
    if (errorCodeRequired != null) {
      app.myErrorCodeRequired = errorCodeRequired;
    }
    final Boolean runWithEditorSettings = getRunWithEditorSettings();
    if (runWithEditorSettings != null) {
      app.myRunWithEditorSettings = runWithEditorSettings;
    }

    final String xsltSchemePath = getXSLTSchemePath();
    if (xsltSchemePath != null) {
      app.myOutputFormat = xsltSchemePath;
    } else {
      final String outputFormat = getOutputFormat();
      if (outputFormat != null) {
        app.myOutputFormat = outputFormat.toLowerCase();
      }
    }
  }

  @Nullable
  protected String determineProjectPath() {
    final String projectPath = getProjectPath();
    return projectPath != null ? projectPath : getDefaultProjectPath();
  }


  @Override
  public void validate() throws CmdlineArgsValidationException {
    // project path
    final String projectPath = determineProjectPath();
    if (projectPath == null) {
      throw new CmdlineArgsValidationException("Project not found");
    }
    else if (!new File(projectPath).exists()) {
      throw new CmdlineArgsValidationException("Project '" + projectPath + "' doesn't exist.");
    }

    // Dir to inspect
    final String dirToInspect = getDirToInspect();
    if (dirToInspect != null && !(new File(dirToInspect).exists())) {
      throw new CmdlineArgsValidationException("Directory '" + dirToInspect + "' doesn't exist.");
    }

    final String xsltSchemePath = getXSLTSchemePath();
    if (xsltSchemePath != null) {
      final File xsltScheme = new File(xsltSchemePath);
      if (!xsltScheme.exists()) {
        throw new CmdlineArgsValidationException("XSLT scheme '" + xsltSchemePath + "' doesn't exist.");
      }
    }

    final String outputFormat = getOutputFormat();
    if (outputFormat != null) {
      StringBuilder builder = new StringBuilder();
      for (InspectionsReportConverter converter : InspectionsReportConverter.EP_NAME.getExtensions()) {
        final String converterFormat = converter.getFormatName();
        if (outputFormat == converterFormat) {
          builder = null;
          break;
        } else {
          if (builder.length() != 0) {
            builder.append(", ");
          }
          builder.append(converterFormat);
        }
      }
      // report error if converter isn't registered.
      if (builder != null) {
        throw new CmdlineArgsValidationException("Unsupported format option '" + outputFormat + "'. Please use one of: " + builder.toString());
      }
    }
  }

  @Override
  public void printHelpAndExit() {
    Args.usage(this);
    System.exit(1);
  }

  protected String getDefaultOutputPath() {
    return getOutputPath() + "/results";
  }

  @Nullable
  protected String getDefaultProjectPath() {
    // current working dir
    return System.getProperty("user.dir");
  }
}
