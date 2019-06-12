// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExternalSystemJavaSdkProvider implements ExternalSystemJdkProvider {
  @NotNull
  @Override
  public SdkType getJavaSdkType() {
    return JavaSdk.getInstance();
  }

  @NotNull
  @Override
  public Sdk getInternalJdk() {
    return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
  }

  @NotNull
  @Override
  public Sdk createJdk(@Nullable String jdkName, @NotNull String homePath) {
    SdkType javaSdk = getJavaSdkType();
    String sdkName = jdkName != null ? jdkName : javaSdk.suggestSdkName(null, homePath);
    return ((JavaSdk)javaSdk).createJdk(sdkName, homePath, !JdkUtil.checkForJdk(homePath));
  }
}
