/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
public class CertificateUtil {
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
    try {
      InputStream stream = new FileInputStream(path);
      try {
        return (X509Certificate)ourFactory.generateCertificate(stream);
      }
      finally {
        stream.close();
      }
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
