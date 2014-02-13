/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Map;

public class EncodingEnvironmentUtil {

  private static final Logger LOG = Logger.getInstance(EncodingEnvironmentUtil.class);
  private static final String LC_ALL = "LC_ALL";
  private static final String LC_CTYPE = "LC_CTYPE";
  private static final String LANG = "LANG";

  /**
   * Sets default encoding on Mac if it's undefined. <br/>
   * On Mac default character encoding is defined by several environment variables: LC_ALL, LC_CTYPE and LANG.
   * See <a href='http://www.gnu.org/software/gettext/manual/html_node/Locale-Environment-Variables.html'>details</a>.
   * <p>
   * Unfortunately, Mac OSX has a special behavior:<br/>
   * These environment variables aren't passed to an IDE, if the IDE is launched from Spotlight.<br/>
   * Unfortunately, even {@link com.intellij.util.EnvironmentUtil#getEnvironment()} doesn't have these variables.<p/>
   * As a result, no encoding environment variables are passed to Ruby/Node.js/Python/other processes that are launched from IDE.
   * Thus, these processes wrongly assume that the default encoding is US-ASCII.
   * <p/>
   *
   * The workaround this method applies is to set LC_CTYPE environment variable if LC_ALL, LC_CTYPE or LANG aren't set before. <br/>
   * LC_CTYPE value is taken from "Settings | File Encodings".
   *
   * @param commandLine GeneralCommandLine instance
   * @param project Project instance if any
   */
  public static void fixDefaultEncodingIfMac(@NotNull GeneralCommandLine commandLine, @Nullable Project project) {
    if (SystemInfo.isMac) {
      if (!isLocaleDefined(commandLine)) {
        Charset charset = getCharset(project);
        commandLine.getEnvironment().put(LC_CTYPE, charset.name());
        if (LOG.isDebugEnabled()) {
          LOG.debug("Fixed mac locale: " + charset.name());
        }
      }
    }
  }

  private static boolean isLocaleDefined(@NotNull GeneralCommandLine commandLine) {
    Map<String, String> env = commandLine.getEnvironment();
    if (isLocaleDefined(env)) {
      return true;
    }
    if (commandLine.isPassParentEnvironment()) {
      // 'parentEnv' calculation logic should be kept in sync with GeneralCommandLine.setupEnvironment
      Map<String, String> parentEnv = PlatformUtils.isAppCode() ? System.getenv() // Temporarily fix for OC-8606
                                                                : EnvironmentUtil.getEnvironmentMap();
      if (isLocaleDefined(parentEnv)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isLocaleDefined(@NotNull Map<String, String> env) {
    return env.containsKey(LC_ALL) || env.containsKey(LC_CTYPE) || env.containsKey(LANG);
  }

  @NotNull
  private static Charset getCharset(@Nullable Project project) {
    Charset charset = null;
    if (project != null) {
      charset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    }
    if (charset == null) {
      charset = EncodingManager.getInstance().getDefaultCharset();
    }
    if (charset == null) {
      charset = CharsetToolkit.UTF8_CHARSET;
    }
    return charset;
  }


}
