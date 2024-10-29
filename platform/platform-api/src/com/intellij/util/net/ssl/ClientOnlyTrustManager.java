package com.intellij.util.net.ssl;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
* @author Mikhail Golubev
*/
public abstract class ClientOnlyTrustManager extends X509ExtendedTrustManager {
  @Override
  public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
    throw new UnsupportedOperationException("Should not be called by client");
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
    throw new UnsupportedOperationException("Should not be called by client");
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
    throw new UnsupportedOperationException("Should not be called by client");
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
    checkServerTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
    checkServerTrusted(chain, authType);
  }
}
