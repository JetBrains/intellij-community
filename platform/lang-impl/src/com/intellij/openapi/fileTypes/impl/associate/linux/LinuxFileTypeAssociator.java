// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate.linux;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.ide.actions.CreateDesktopEntryAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationException;
import com.intellij.openapi.fileTypes.impl.associate.SystemFileTypeAssociator;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LinuxFileTypeAssociator implements SystemFileTypeAssociator {
  @Override
  public void associateFileTypes(@NotNull List<FileType> fileTypes) throws OSFileAssociationException {
    updateMimeTypes(fileTypes.stream().map(MimeTypeDescription::new).sorted().toList());
  }

  private static final String LOCAL_MIME_DIR = ".local/share/mime";
  private static final String LOCAL_APP_DIR = ".local/share/applications";
  private static final String LOCAL_MIME_PACKAGES_PATH = LOCAL_MIME_DIR + "/packages";
  private static final String EXTENSIONS_FILE_NAME = "jb-" + PlatformUtils.getPlatformPrefix() + "-extensions.xml";

  private static void updateMimeTypes(List<MimeTypeDescription> descriptions) throws OSFileAssociationException {
    try {
      var mimeFile = Path.of(SystemProperties.getUserHome(), LOCAL_MIME_PACKAGES_PATH, EXTENSIONS_FILE_NAME);
      Files.createDirectories(mimeFile.getParent());
      JDOMUtil.writeDocument(createMimeFile(descriptions), new BufferedOutputStream(Files.newOutputStream(mimeFile)), "\n");

      var mimeDir = Path.of(SystemProperties.getUserHome(), LOCAL_MIME_DIR);
      runCommand(new GeneralCommandLine("xdg-mime", "install", "--mode", "user", "--novendor", mimeDir.toString()));

      var desktopEntryName = CreateDesktopEntryAction.getDesktopEntryName();
      CreateDesktopEntryAction.createDesktopEntry(false);

      @SuppressWarnings("SSBasedInspection") var mimeTypes = descriptions.stream().map(mtd -> mtd.type).toList();
      runCommand(new GeneralCommandLine("xdg-mime", "default", desktopEntryName).withParameters(mimeTypes));

      var appDir = Path.of(SystemProperties.getUserHome(), LOCAL_APP_DIR);
      runCommand(new GeneralCommandLine("update-desktop-database", appDir.toString()));
    }
    catch (Exception e) {
      throw new OSFileAssociationException(e);
    }
  }

  private static Document createMimeFile(List<MimeTypeDescription> descriptions) {
    var ns = "http://www.freedesktop.org/standards/shared-mime-info";
    var root = new Element("mime-info", ns);
    for (var description : descriptions) {
      var mimeType = new Element("mime-type", ns).setAttribute("type", description.type);
      mimeType.addContent(new Element("comment", ns).addContent(description.comment));
      for (var pattern : description.globPatterns) {
        mimeType.addContent(new Element("glob", ns).setAttribute("pattern", pattern));
      }
      root.addContent(mimeType);
    }
    return new Document(root);
  }

  private static void runCommand(GeneralCommandLine command) throws ExecutionException, OSFileAssociationException {
    var output = new CapturingProcessHandler(command).runProcess(1000);
    if (output.isTimeout()) {
      throw new OSFileAssociationException("Failed to run " + command.getExePath() + " in 1 sec");
    }
    String errorMessage = output.getStderr();
    if (!errorMessage.isBlank()) {
      throw new OSFileAssociationException(command.getExePath() + ": " + errorMessage);
    }
  }
}
