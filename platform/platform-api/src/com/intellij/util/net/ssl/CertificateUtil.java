package com.intellij.util.net.ssl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
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
}
