// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.network.CefCookie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

/**
 * A wrapper over {@link CefCookie}.
 *
 * @author Aleksey.Rostovskiy
 */
public class JBCefCookie {
  private final CefCookie myCefCookie;

  public JBCefCookie(CefCookie cefCookie) {
    myCefCookie = cefCookie;
  }

  public JBCefCookie(@NotNull String name,
                     @NotNull String value,
                     @NotNull String domain,
                     @NotNull String path,
                     boolean secure,
                     boolean httponly,
                     @Nullable Date creation,
                     @Nullable Date lastAccess,
                     boolean hasExpires,
                     @Nullable Date expires) {
    this(new CefCookie(name, value, domain, path, secure, httponly, creation, lastAccess, hasExpires, expires));
  }

  @SuppressWarnings("unused")
  public JBCefCookie(@NotNull String name,
                     @NotNull String value,
                     @NotNull String domain,
                     @NotNull String path,
                     boolean secure,
                     boolean httponly) {
    this(name, value, domain, path, secure, httponly, null, null, false, null);
  }

  @NotNull
  public CefCookie getCefCookie() {
    return myCefCookie;
  }

  @NotNull
  public String getName() {
    return myCefCookie.name;
  }

  @NotNull
  public String getValue() {
    return myCefCookie.value;
  }

  @NotNull
  public String getDomain() {
    return myCefCookie.domain;
  }

  @NotNull
  public String getPath() {
    return myCefCookie.path;
  }

  public boolean isSecure() {
    return myCefCookie.secure;
  }

  public boolean isHttpOnly() {
    return myCefCookie.httponly;
  }

  @Nullable
  public Date getCreation() {
    return myCefCookie.creation;
  }

  @Nullable
  public Date getLastAccess() {
    return myCefCookie.lastAccess;
  }

  @SuppressWarnings("unused")
  public boolean hasExpires() {
    return myCefCookie.hasExpires;
  }

  @Nullable
  public Date getExpires() {
    return myCefCookie.expires;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JBCefCookie cookie = (JBCefCookie)o;
    return getName().equals(cookie.getName()) &&
           getValue().equals(cookie.getValue()) &&
           getDomain().equals(cookie.getDomain()) &&
           getPath().equals(cookie.getPath()) &&
           isSecure() == cookie.isSecure() &&
           isHttpOnly() == cookie.isHttpOnly();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getValue(), getDomain(), getPath(), isSecure(), isHttpOnly());
  }
}
