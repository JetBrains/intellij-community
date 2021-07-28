// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.dependencies;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public abstract class DependencyAuthenticationDataProvider {
  public abstract AuthenticationData provideAuthenticationData(String url);

  public static final class AuthenticationData {
    private final String userName;
    private final String password;

    public AuthenticationData(String userName, String password) {
      this.userName = userName;
      this.password = password;
    }

    public String getUserName() {
      return userName;
    }

    public String getPassword() {
      return password;
    }
  }
}
