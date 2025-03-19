// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate.macos;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.associate.OSAssociateFileTypesUtil;
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class UniformIdentifierUtil {
  /**
   * Known system file types
   * See <a href="https://developer.apple.com/library/archive/documentation/Miscellaneous/Reference/UTIRef/Articles/System-DeclaredUniformTypeIdentifiers.html>System-Declared Uniform Type Identifiers</a>
   */
  private static final Map<String,String[]> TYPE_MAP          = new HashMap<>();
  public static final String                CONTENT_TYPE_ATTR = "kMDItemContentType";

  static {
    mapType("PLAIN_TEXT", "public.plain-text");
    mapType("HTML", "public.html");
    mapType("XML", "public.xml");
    mapType("ObjectiveC",
            "public.objective-c-source",
            "public.objective-c-plus-plus-source",
            "public.c-plus-plus-source",
            "public.c-source",
            "public.c-header",
            "public.c-plus-plus-header");
    mapType("JAVA", "com.sun.java-source");
    mapType("JavaScript", "com.netscape.javascript-source");
    mapType("Shell Script", "public.shell-script");
    mapType("Python", "public.python-script");
    mapType("Ruby", "public.ruby-script");
  }

  private static void mapType(@NotNull String ijType, String... macType) {
    TYPE_MAP.put(ijType, macType);
  }

  static String @NotNull [] getURIs(@NotNull FileType fileType) throws OSFileAssociationException {
    String[] uris = TYPE_MAP.get(fileType.getName());
    if (uris != null) {
      return uris;
    }
    try {
      List<String> uriList = new ArrayList<>();
      for (String ext : OSAssociateFileTypesUtil.getExtensions(fileType)) {
        String uri = getUriByExtension(ext);
        if (uri != null) uriList.add(uri);
      }
      return ArrayUtil.toStringArray(uriList);
    }
    catch (IOException | ExecutionException e) {
      throw new OSFileAssociationException(e.getMessage());
    }
  }

  private static @Nullable String getUriByExtension(@NotNull String extension) throws IOException, ExecutionException, OSFileAssociationException {
    File file = FileUtil.createTempFile("content_", "." + extension);
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("/usr/bin/mdls");
    commandLine.addParameter("-name");
    commandLine.addParameter(CONTENT_TYPE_ATTR);
    commandLine.addParameter(file.getPath());
    Ref<String> contentTypeValue = Ref.create();
    OSProcessHandler handler = new OSProcessHandler(commandLine);
    StringBuilder errMessage = new StringBuilder();
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event,
                                  @NotNull Key outputType) {
        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          errMessage.append(event.getText());
        }
        else if (ProcessOutputTypes.STDOUT.equals(outputType)) {
          String text = event.getText();
          if (text.contains(CONTENT_TYPE_ATTR)) {
            contentTypeValue.set(extractContentTypeValue(text));
          }
        }
      }
    });
    handler.startNotify();
    if (!handler.waitFor(1000)) {
      throw new OSFileAssociationException("Failed to run mdls within 1 sec");
    }
    if (handler.getExitCode() != null && handler.getExitCode() != 0) {
      throw new OSFileAssociationException("mdls failed with exit code " + handler.getExitCode()
                                           + ", error message: " + errMessage);
    }
    return contentTypeValue.get();
  }

  private static @Nullable String extractContentTypeValue(@NotNull String contentType) {
    int eqPos = contentType.indexOf("=");
    if (eqPos > 0) {
      String result = contentType.substring(eqPos + 1).trim();
      result = StringUtil.trimStart(result, "\"");
      result = StringUtil.trimEnd(result, "\"");
      if (!result.startsWith("dyn.")) return result;
    }
    return null;
  }
}
