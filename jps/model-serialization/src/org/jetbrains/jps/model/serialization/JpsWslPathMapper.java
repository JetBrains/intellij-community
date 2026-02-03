// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.StreamUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public final class JpsWslPathMapper implements JpsPathMapper {
  private static final List<@NotNull String> WSL_PREFIXES = Arrays.asList("//wsl$/", "//wsl.localhost/");
  private @Nullable String myWslRootPrefix;

  @Override
  public @Nullable String mapUrl(@Nullable String url) {
    if (url == null) return null;
    for (String wslPrefix : WSL_PREFIXES) {
      if (url.contains(wslPrefix)) {
        int startPos = url.indexOf(wslPrefix);
        int endPos = url.indexOf('/', startPos + wslPrefix.length());
        if (endPos >= 0) {
          return url.substring(0, startPos) + url.substring(endPos);
        }
      }
    }
    if (url.startsWith(JpsPathUtil.FILE_URL_PREFIX)) {
      return JpsPathUtil.FILE_URL_PREFIX + mapPath(url.substring(JpsPathUtil.FILE_URL_PREFIX.length()));
    }
    if (url.startsWith(JpsPathUtil.JAR_URL_PREFIX)) {
      int index = url.indexOf(JpsPathUtil.JAR_SEPARATOR);
      if (index >= 0) {
        return JpsPathUtil.JAR_URL_PREFIX + mapPath(url.substring(JpsPathUtil.JAR_URL_PREFIX.length(), index)) + url.substring(index);
      }
    }

    return url;
  }

  private @NotNull String mapPath(@NotNull String path) {
    if (myWslRootPrefix == null || path.indexOf(':') != 1) {
      String wslPath;
      try {
        wslPath = runWslPath(path);
      }
      catch (IOException | InterruptedException e) {
        return path;
      }
      if (path.indexOf(':') == 1) {
        int pathLengthAfterDriveLetter = path.length() - 2;
        myWslRootPrefix = wslPath.substring(0, wslPath.length() - pathLengthAfterDriveLetter - 1);
      }
      return wslPath;
    }

    return myWslRootPrefix + Character.toLowerCase(path.charAt(0)) + FileUtilRt.toSystemIndependentName(path.substring(2));
  }

  private static String runWslPath(String path) throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder("/usr/bin/wslpath", path);
    Process process = processBuilder.start();
    process.waitFor();
    if (process.exitValue() == 0) {
      return StreamUtil.readText(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)).trim();
    }
    return path;
  }
}
