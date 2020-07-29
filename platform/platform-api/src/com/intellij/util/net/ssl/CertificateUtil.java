// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net.ssl;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Names in constants match
 * <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html">Standard Algorithm Name Documentation</a>.
 *
 * @author Mikhail Golubev
 */
public final class CertificateUtil {
  public static final String X509 = "X.509";
  public static final String JKS = "JKS";
  public static final String PKCS12 = "PKCS12";
  public static final String PKIX = "PKIX";
  public static final String TLS = "TLS";

  private static final CertificateFactory ourFactory = createFactory();

  private static CertificateFactory createFactory() {
    try {
      return CertificateFactory.getInstance(X509);
    }
    catch (CertificateException e) {
      throw new RuntimeException("Can't initialize X.509 certificate factory", e);
    }
  }

  private CertificateUtil() { }

  @Nullable
  public static X509Certificate loadX509Certificate(@NotNull String path) {
    try (InputStream stream = new FileInputStream(path)) {
      return (X509Certificate)ourFactory.generateCertificate(stream);
    }
    catch (Exception e) {
      Logger.getInstance(CertificateUtil.class).error("Can't add certificate for path: " + path, e);
      return null;
    }
  }

  /**
   * @return subjects common name, usually it's domain name pattern, e.g. *.github.com
   */
  public static String getCommonName(@NotNull X509Certificate certificate) {
    return new CertificateWrapper(certificate).getSubjectField(CertificateWrapper.CommonField.COMMON_NAME);
  }
}
