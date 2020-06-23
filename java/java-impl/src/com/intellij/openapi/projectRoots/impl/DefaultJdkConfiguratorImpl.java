// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.DefaultJdkConfigurator;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;

final class DefaultJdkConfiguratorImpl implements DefaultJdkConfigurator {
  @Override
  public String guessJavaHome() {
    Collection<String> homePaths = JavaSdk.getInstance().suggestHomePaths();
    return ContainerUtil.getFirstItem(homePaths);
  }
}