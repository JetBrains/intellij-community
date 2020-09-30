// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate.macos;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.ide.lightEdit.actions.associate.FileAssociationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

@SuppressWarnings("SameParameterValue")
public class PListBuddyWrapper {
  public final static String UTIL_PATH = "/usr/libexec/PListBuddy";
  public final static String PLIST_PATH = SystemProperties.getUserHome() +
                                          "/Library/Preferences/com.apple.LaunchServices/com.apple.launchservices.secure.plist";

  private final static Logger LOG = Logger.getInstance(PListBuddyWrapper.class);

  enum OutputType {
    DEFAULT,
    XML
  }

  CommandResult runCommand(@NotNull String command) throws FileAssociationException {
    return runCommand(OutputType.DEFAULT, command);
  }

  CommandResult runCommand(@NotNull OutputType outputType, String... commands) throws FileAssociationException {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(UTIL_PATH);
    for (String command : commands) {
      if (OutputType.XML.equals(outputType)) {
        commandLine.addParameter("-x");
      }
      commandLine.addParameter("-c");
      commandLine.addParameter(command);
    }
    commandLine.addParameter(PLIST_PATH);
    StringBuilder errorMessage = new StringBuilder();
    StringBuilder output = new StringBuilder();
    try {
      OSProcessHandler processHandler = new OSProcessHandler(commandLine);
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event,
                                    @NotNull Key outputType) {
          if (ProcessOutputTypes.STDOUT.equals(outputType)) {
            output.append(event.getText());
          }
          if (ProcessOutputTypes.STDERR.equals(outputType)) {
            errorMessage.append(event.getText());
          }
        }
      });
      processHandler.startNotify();
      if (!processHandler.waitFor(1000)) {
        throw new FileAssociationException("Failed to run update-mime-database in 1 sec");
      }
      if (errorMessage.length() > 0) {
        throw new FileAssociationException("PListBuddy returned: " + errorMessage.toString());
      }
      return new CommandResult(ObjectUtils.notNull(processHandler.getExitCode(), -1), output.toString());
    }
    catch (ExecutionException exe) {
      throw new FileAssociationException("Can't run PListBuddy: " + exe.getLocalizedMessage());
    }
  }

  @NotNull
  Document readData(@NotNull String dataId) throws FileAssociationException {
    CommandResult result = runCommand(OutputType.XML, "Print " + dataId);
    if (result.retCode != 0) {
      throw new FileAssociationException("PListBuddy returned " + result.retCode);
    }
    if (result.output != null && !result.output.startsWith("<?xml")) {
      throw new FileAssociationException("Unexpected output: " + truncateOutput(result.output, 20));
    }
    if (result.output == null) {
      throw new FileAssociationException("Empty output for Print " + dataId + " command");
    }
    return parseXml(result.output);
  }

  @NotNull
  Document parseXml(@NotNull String xmlString) throws FileAssociationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      StringReader stringReader = new StringReader(xmlString);
      InputSource source = new InputSource(stringReader);
      return builder.parse(source);
    }
    catch (ParserConfigurationException e) {
      LOG.error(e);
    }
    catch (SAXException | IOException e) {
      throw new FileAssociationException("XML read error: " + e.getMessage());
    }
    throw new FileAssociationException("XML parsing error, logged.");
  }

  private static String truncateOutput(@NotNull String output, int maxChars) {
    return output.length() < maxChars ? output : output.substring(0, maxChars) + "...";
  }

  static class CommandResult {
    int retCode;
    String output;

    private CommandResult(int retCode, String output) {
      this.retCode = retCode;
      this.output = output;
    }
  }
}
