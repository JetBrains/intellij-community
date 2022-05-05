// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.security.cert.X509Certificate;
import java.util.Collection;

@ApiStatus.Internal
public interface OsCertificatesService {
  @NotNull
  Collection<X509Certificate> getCustomOsSpecificTrustedCertificates();

  static OsCertificatesService getInstance() {
    return ApplicationManager.getApplication().getService(OsCertificatesService.class);
  }
}
