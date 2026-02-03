// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class HttpHeaderDocumentation {
  private static final String CC_LICENSE =
    " is licensed under <a href=\"https://creativecommons.org/licenses/by-sa/2.5/\">CC-BY-SA 2.5</a>.";

  private static final String URL_PREFIX = "https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/";
  private static final String RFC_PREFIX = "https://tools.ietf.org/html/rfc";

  private final String myName;
  private final String myRfc;
  private final String myRfcTitle;
  private final String myDescription;
  private final boolean myIsDeprecated;

  private HttpHeaderDocumentation(String name, String rfc, String rfcTitle, String description, boolean isDeprecated) {
    myName = name;
    myRfc = rfc;
    myRfcTitle = rfcTitle;
    myDescription = description;
    myIsDeprecated = isDeprecated;
  }

  HttpHeaderDocumentation(String name) {
    this(name, "", "", "", false);
  }

  public static @Nullable HttpHeaderDocumentation read(@NotNull JsonObject obj) {
    final String name = getAsString(obj, "name");
    if (StringUtil.isNotEmpty(name)) {
      final String rfcTitle = getAsString(obj, "rfc-title");
      final String rfcRef = getAsString(obj, "rfc-ref");
      final String descr = getAsString(obj, "descr");

      final JsonElement obsolete = obj.get("obsolete");
      final boolean isObsolete = obsolete != null && obsolete.isJsonPrimitive() && obsolete.getAsBoolean();
      return new HttpHeaderDocumentation(name, rfcRef, rfcTitle, descr, isObsolete);
    }
    return null;
  }

  private static @NotNull String getAsString(@NotNull JsonObject obj, @NotNull String name) {
    final JsonElement element = obj.get(name);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
  }

  public @Nullable @NlsSafe String generateDoc() {
    // we have only english documentation from Mozilla so far
    if (StringUtil.isNotEmpty(myDescription)) {
      final StringBuilder out = new StringBuilder().append(myDescription);
      if (StringUtil.isNotEmpty(myRfc) && StringUtil.isNotEmpty(myRfcTitle)) {
        out.append("<br/><br/>");
        out.append("<a href=\"").append(RFC_PREFIX).append(myRfc).append("\">").append(myRfcTitle).append("</a>");
      }

      final String url = getUrl();
      out.append("<br/><br/>");
      out.append("<a href=\"").append(url).append("\">").append(getName()).append("</a> by ");
      out.append("<a href=\"").append(url).append("$history").append("\">").append("Mozilla Contributors").append("</a>");
      out.append(CC_LICENSE);
      return out.toString();
    }
    return null;
  }

  public @NotNull String getName() {
    return myName;
  }

  public boolean isDeprecated() {
    return myIsDeprecated;
  }

  public @NotNull String getDescription() {
    return myDescription;
  }

  public @NotNull String getUrl() {
    return URL_PREFIX + getName();
  }
}
