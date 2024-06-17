// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import org.jetbrains.annotations.ApiStatus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class SshPrompts {
  public static final Pattern PASSPHRASE_PROMPT = Pattern.compile("\\r?Enter passphrase for( key)? '?(?<keyfile>[^']*)'?:\\s?");
  public static final Pattern PASSWORD_PROMPT = Pattern.compile("(?<username>.*)'s password:\\s?");
  public static final Pattern PKCS_PIN_TOKEN_PROMPT = Pattern.compile("\\r?Enter PIN for '?(?<tokenLabel>[^']*)'?:\\s?");
  public static final String PASSWORD_PROMPT_PREFIX = "password for";
  public static final String PASSWORD_PROMPT_SUFFIX = "password:";
  public static final String CONFIRM_CONNECTION_PROMPT = "Are you sure you want to continue connecting";
  public static final String REMOTE_HOST_IDENTIFICATION_HAS_CHANGED = "remote host identification has changed";

  public static String extractKeyPath(Matcher matcher) {
    return matcher.group("keyfile");
  }

  public static String extractPkcsTokenLabel(Matcher matcher) {
    return matcher.group("tokenLabel");
  }

  public static String extractUsername(Matcher matcher) {
    return matcher.group("username");
  }
}
