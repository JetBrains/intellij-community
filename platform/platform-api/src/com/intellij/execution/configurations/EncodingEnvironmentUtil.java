// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;

public class EncodingEnvironmentUtil {
  private static final Logger LOG = Logger.getInstance(EncodingEnvironmentUtil.class);

  private static final String LC_ALL = "LC_ALL";
  private static final String LC_CTYPE = "LC_CTYPE";
  private static final String LANG = "LANG";

  /**
   * @deprecated GeneralCommandLine now contains the variable by default
   *
   * Sets default encoding on Mac if it's undefined. <br/>
   * On Mac default character encoding is defined by several environment variables: LC_ALL, LC_CTYPE and LANG.
   * See <a href='http://www.gnu.org/software/gettext/manual/html_node/Locale-Environment-Variables.html'>details</a>.
   * <p/>
   * Unfortunately, Mac OSX has a special behavior:<br/>
   * These environment variables aren't passed to an IDE, if the IDE is launched from Spotlight.<br/>
   * Unfortunately, even {@link EnvironmentUtil#getEnvironment()} doesn't have these variables.<p/>
   * As a result, no encoding environment variables are passed to Ruby/Node.js/Python/other processes that are launched from IDE.
   * Thus, these processes wrongly assume that the default encoding is US-ASCII.
   * <p/>
   * <p/>
   * The workaround this method applies is to set LC_CTYPE environment variable if LC_ALL, LC_CTYPE or LANG aren't set before. <br/>
   * LC_CTYPE value is taken from "Settings | File Encodings".
   *
   * @param commandLine GeneralCommandLine instance
   */
  @Deprecated
  public static void setLocaleEnvironmentIfMac(@NotNull GeneralCommandLine commandLine) {
    if (SystemInfo.isMac && !isLocaleDefined(commandLine)) {
      setLocaleEnvironment(commandLine.getEnvironment(), commandLine.getCharset());
    }
  }

  /**
   * @deprecated use {@link EnvironmentUtil#getEnvironmentMap()}
   *
   * Sets default encoding on Mac if it's undefined. <br/>
   */
  @Deprecated
  public static void setLocaleEnvironmentIfMac(@NotNull Map<String, String> env, @NotNull Charset charset) {
    if (SystemInfo.isMac && !isLocaleDefined(env)) {
      setLocaleEnvironment(env, charset);
    }
  }

  private static void setLocaleEnvironment(@NotNull Map<String, String> env, @NotNull Charset charset) {
    env.put(LC_CTYPE, formatLocaleValue(charset));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fixed mac locale: " + charset.name());
    }
  }

  @NotNull
  private static String formatLocaleValue(@NotNull Charset charset) {
    Locale locale = Locale.getDefault();
    String language = locale.getLanguage();
    String country = locale.getCountry();
    return (language.isEmpty() || country.isEmpty() ? "en_US" : language + "_" + country) + "." + charset.name();
  }

  private static boolean isLocaleDefined(@NotNull GeneralCommandLine commandLine) {
    return isLocaleDefined(commandLine.getEnvironment()) || isLocaleDefined(commandLine.getParentEnvironment());
  }

  private static boolean isLocaleDefined(@NotNull Map<String, String> env) {
    return !env.isEmpty() && (env.containsKey(LC_CTYPE) || env.containsKey(LC_ALL) || env.containsKey(LANG));
  }
}
