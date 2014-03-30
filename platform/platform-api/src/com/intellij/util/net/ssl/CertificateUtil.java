package com.intellij.util.net.ssl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * @author Mikhail Golubev
 */
public class CertificateUtil {
  private static final Logger LOG = Logger.getInstance(CertificateUtil.class);

  private static final CertificateFactory ourFactory = createFactory();

  private static CertificateFactory createFactory() {
    try {
      return CertificateFactory.getInstance("X509");
    }
    catch (CertificateException e) {
      throw new AssertionError("Can't initialize X509 certificate factory");
    }
  }

  // Standard Names
  // See complete reference at http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html
  // certificate format
  @Nls public static final String X509 = "X.509";
  // Java Key Store - standard type of keystores used by keytool utility
  @Nls public static final String JKS = "JKS";
  // another standard type of keystore
  @Nls public static final String PKCS12 = "PKCS12";
  // type of trust manager factory
  @Nls public static final String PKIX = "PKIX";
  @Nls public static final String TLS = "TLS";

  /**
   * Utility class
   */
  private CertificateUtil() {
    // empty
  }

  @Nullable
  public static X509Certificate loadX509Certificate(@NotNull String path) {
    InputStream stream = null;
    try {
      stream = new FileInputStream(path);
      return (X509Certificate)ourFactory.generateCertificate(stream);
    }
    catch (Exception e) {
      LOG.error("Can't add certificate for path: " + path, e);
      return null;
    }
    finally {
      StreamUtil.closeStream(stream);
    }
  }

  /**
   * @return subjects common name, usually it's domain name pattern, e.g. *.github.com
   */
  public static String getCommonName(@NotNull X509Certificate certificate) {
    return new CertificateWrapper(certificate).getSubjectField(CertificateWrapper.CommonField.COMMON_NAME);
  }
}
