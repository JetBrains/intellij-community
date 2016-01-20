/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.builtInWebServer.ssi;

import io.netty.buffer.ByteBufUtf8Writer;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Implements the Server-side #fsize command
 *
 * @author Bip Thelin
 * @author Paul Speed
 * @author Dan Sandberg
 * @author David Becker
 */
final class SsiFsize implements SsiCommand {
  private static final int ONE_KILOBYTE = 1024;
  private static final int ONE_MEGABYTE = 1024 * 1024;

  @Override
  public long process(@NotNull SsiProcessingState state, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer) {
    long lastModified = 0;
    String configErrMsg = state.configErrorMessage;
    for (int i = 0; i < paramNames.size(); i++) {
      String paramName = paramNames.get(i);
      String paramValue = paramValues[i];
      String substitutedValue = state.substituteVariables(paramValue);
      if (paramName.equalsIgnoreCase("file") || paramName.equalsIgnoreCase("virtual")) {
        boolean virtual = paramName.equalsIgnoreCase("virtual");
        lastModified = state.ssiExternalResolver.getFileLastModified(substitutedValue, virtual);
        writer.write(formatSize(state.ssiExternalResolver.getFileSize(substitutedValue, virtual), state.configSizeFmt));
      }
      else {
        SsiProcessorKt.getLOG().info("#fsize--Invalid attribute: " + paramName);
        writer.write(configErrMsg);
      }
    }
    return lastModified;
  }

  // We try to mimic Apache here, as we do everywhere
  // All the 'magic' numbers are from the util_script.c Apache source file.
  private static String formatSize(long size, @NotNull String format) {
    if (format.equalsIgnoreCase("bytes")) {
      return new DecimalFormat("#,##0").format(size);
    }

    String result;
    if (size == 0) {
      result = "0k";
    }
    else if (size < ONE_KILOBYTE) {
      result = "1k";
    }
    else if (size < ONE_MEGABYTE) {
      result = Long.toString((size + 512) / ONE_KILOBYTE) + "k";
    }
    else if (size < 99 * ONE_MEGABYTE) {
      result = new DecimalFormat("0.0M").format(size / (double)ONE_MEGABYTE);
    }
    else {
      result = Long.toString((size + (529 * ONE_KILOBYTE)) / ONE_MEGABYTE) + "M";
    }

    int charsToAdd = 5 - result.length();
    if (charsToAdd < 0) {
      throw new IllegalArgumentException("Num chars can't be negative");
    }

    if (charsToAdd == 0) {
      return result;
    }

    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < charsToAdd; i++) {
      buf.append(' ');
    }
    return buf.append(result).toString();
  }
}