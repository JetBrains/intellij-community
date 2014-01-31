package com.intellij.util.net.ssl;

import java.security.cert.X509Certificate;
import java.util.EventListener;

/**
 * @author Mikhail Golubev
 */
public interface CertificateListener extends EventListener {
  void certificateAdded(X509Certificate certificate);

  void certificateRemoved(X509Certificate certificate);
}
