// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate.linux;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.ide.lightEdit.actions.associate.FileAssociationException;
import com.intellij.openapi.util.Key;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <a href="https://www.freedesktop.org/wiki/Specifications/shared-mime-info-spec">FreeDesktop Mime Info</a>
 */
@SuppressWarnings("SameParameterValue")
final
class LinuxMimeTypeUpdater {
  private static final String LOCAL_MIME_DIR = ".local/share/mime";
  private static final String LOCAL_APP_DIR = ".local/share/applications";
  private static final String LOCAL_MIME_PACKAGES_PATH = LOCAL_MIME_DIR + "/packages";
  private static final String EXTENSIONS_FILE_NAME = "jb-" + PlatformUtils.getPlatformPrefix() + "-extensions.xml";

  private static final String MIME_INFO_TAG = "mime-info";
  private static final String MIME_XMLNS = "http://www.freedesktop.org/standards/shared-mime-info";
  private static final String MIME_TYPE_TAG = "mime-type";
  private static final String MIME_TYPE_ATTR = "type";

  private static final String OS_MIME_UTIL = "xdg-mime";
  private static final String[] OS_MIME_UTIL_INSTALL_PARAMS = new String[] {
    "install", "--mode", "user", "--novendor", "$file"
  };
  private static final String[] OS_MIME_UTIL_DEFAULT_APP_PARAMS = new String[] {
    "default", "$desktop-entry"
  };

  private static final String OS_UPDATE_DESKTOP_DB_COMMAND = "update-desktop-database";

  private LinuxMimeTypeUpdater() {
  }

  static void updateMimeTypes(@NotNull List<MimeTypeDescription> mimeTypeDescriptions) throws FileAssociationException {
    try {
      createMimeFile(mimeTypeDescriptions);
      runCommand(OS_MIME_UTIL,
                 makeParamList(OS_MIME_UTIL_INSTALL_PARAMS, System.getProperty("user.home") + File.separator + LOCAL_MIME_DIR));
      String desktopEntry = LocalDesktopEntryCreator.createDesktopEntry();
      List<String> defaultAppParams = makeParamList(OS_MIME_UTIL_DEFAULT_APP_PARAMS, desktopEntry);
      defaultAppParams.addAll(
        ContainerUtil.map(mimeTypeDescriptions, description -> description.getType())
      );
      runCommand(OS_MIME_UTIL, defaultAppParams);
      runCommand(OS_UPDATE_DESKTOP_DB_COMMAND, Collections.singletonList(System.getProperty("user.home") + File.separator + LOCAL_APP_DIR));
    }
    catch (IOException | XMLStreamException | ExecutionException e) {
      throw new FileAssociationException(e);
    }
  }


  static void createMimeFile(@NotNull List<MimeTypeDescription> mimeTypeDescriptions) throws IOException, XMLStreamException {
    XMLOutputFactory outputFactory =  XMLOutputFactory.newInstance();
    final FileWriter fileWriter = new FileWriter(
      System.getProperty("user.home") + File.separator + LOCAL_MIME_PACKAGES_PATH + File.separator + EXTENSIONS_FILE_NAME);
    XMLStreamWriter writer = outputFactory.createXMLStreamWriter(fileWriter);
    try {
      writer.writeStartDocument("UTF-8", "1.0");
      writer.writeCharacters("\n");
      writer.writeStartElement(MIME_INFO_TAG);
      writer.writeAttribute("xmlns", MIME_XMLNS);
      writer.writeCharacters("\n");
      for (MimeTypeDescription description : mimeTypeDescriptions) {
        writeDescription(writer, description);
      }
      writer.writeEndElement();
      writer.writeEndDocument();
    }
    finally {
      writer.flush();
      writer.close();
    }
  }

  private static void writeDescription(@NotNull XMLStreamWriter writer, @NotNull MimeTypeDescription description)
    throws XMLStreamException {
    writer.writeCharacters("  ");
    writer.writeStartElement(MIME_TYPE_TAG);
    writer.writeAttribute(MIME_TYPE_ATTR, description.getType());
    writer.writeCharacters("\n");

    writer.writeCharacters("    ");
    writer.writeStartElement("comment");
    writer.writeCharacters(description.getComment());
    writer.writeEndElement();
    writer.writeCharacters("\n");

    for (String pattern : description.getGlobPatterns()) {
      writer.writeCharacters("    ");
      writer.writeStartElement("glob");
      writer.writeAttribute("pattern", pattern);
      writer.writeEndElement();
      writer.writeCharacters("\n");
    }

    writer.writeCharacters("  ");
    writer.writeEndElement();
    writer.writeCharacters("\n");
  }

  private static void runCommand(@NotNull String command, List<String> params) throws ExecutionException, FileAssociationException {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(command);
    for (String param : params) {
      commandLine.addParameter(param);
    }
    StringBuilder errorMessage = new StringBuilder();
    OSProcessHandler mimeDatabaseUpdateHandler = new OSProcessHandler(commandLine);
    mimeDatabaseUpdateHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event,
                                  @NotNull Key outputType) {
        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          errorMessage.append(event.getText());
        }
      }
    });
    mimeDatabaseUpdateHandler.startNotify();
    if (!mimeDatabaseUpdateHandler.waitFor(1000)) {
      throw new FileAssociationException("Failed to run update-mime-database in 1 sec");
    }
    if (errorMessage.length() > 0) {
      throw new FileAssociationException(OS_MIME_UTIL + " returned: " + errorMessage.toString());
    }
  }

  private static List<String> makeParamList(String[] args, String... params) {
    List<String> result = new ArrayList<>();
    int count = 0;
    for (String arg : args) {
      if (arg.startsWith("$")) {
        if (count >= params.length) {
          throw new RuntimeException("Missing argument " + arg);
        }
        result.add(params[count ++]);
      }
      else {
        result.add(arg);
      }
    }
    return result;
  }
}
