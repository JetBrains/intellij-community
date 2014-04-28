package com.intellij.util.net.ssl;

import com.intellij.openapi.ui.DialogWrapper;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;

/**
* @author Mikhail Golubev
*/ /*
 * BrowserCompatibleHostnameVerifier has all final/package-private methods, so direct
 * inheritance from it makes no sense. Inheriting from AbstractVerifier also makes no sense, because
 * the only method I can override verify(String, String[], String[]), and I have no access to certificate for the dialog
 * in this case. Why the heck verify(String, X509Certificate) is final? It basically means, that I should copy half of the
 * AbstractVerifier here, which gives me a strong feel of stupidity.
 *
 * I submit a bug about this problem https://issues.apache.org/jira/browse/HTTPCLIENT-1449 and it seems to be
 * resolved in httpclient4.4.
 */
class ConfirmingHostnameVerifier implements X509HostnameVerifier {
  private final X509HostnameVerifier myVerifier;

  public ConfirmingHostnameVerifier(@NotNull X509HostnameVerifier verifier) {
    myVerifier = verifier;
  }

  // Copied from httpclient 4.2 sources, read class level commentary for explanation.
  @Override
  public void verify(String host, SSLSocket ssl) throws IOException {
    if (host == null) {
      throw new NullPointerException("host to verify is null");
    }

    SSLSession session = ssl.getSession();
    if (session == null) {
      // In our experience this only happens under IBM 1.4.x when
      // spurious (unrelated) certificates show up in the server'
      // chain.  Hopefully this will unearth the real problem:
      final InputStream in = ssl.getInputStream();
      in.available();
      // If ssl.getInputStream().available() didn't cause an
      // exception, maybe at least now the session is available?
      session = ssl.getSession();
      if (session == null) {
        // If it's still null, probably a startHandshake() will
        // unearth the real problem.
        ssl.startHandshake();

        // Okay, if we still haven't managed to cause an exception,
        // might as well go for the NPE.  Or maybe we're okay now?
        session = ssl.getSession();
      }
    }

    final Certificate[] certs = session.getPeerCertificates();
    final X509Certificate x509 = (X509Certificate)certs[0];
    verify(host, x509);
  }

  @Override
  public void verify(final String host, final X509Certificate cert) throws SSLException {
    if (!CertificateManager.getInstance().getState().CHECK_HOSTNAME) {
      return;
    }
    try {
      myVerifier.verify(host, cert);
    }
    catch (SSLException e) {
      //noinspection ConstantConditions
      if (!accepted(host, cert)) {
        throw e;
      }
      // TODO: inclusion in some kind of persistent settings
      // Read/Write lock to protect storage?
    }
  }

  private static boolean accepted(final String host, final X509Certificate cert) {
    return CertificateManager.showAcceptDialog(new Callable<DialogWrapper>() {
      @Override
      public DialogWrapper call() throws Exception {
        return CertificateWarningDialog.createHostnameMismatchWarning(cert, host);
      }
    });
  }

  // Copied from httpclient 4.2 sources, read class level commentary for explanation.
  @Override
  public boolean verify(String host, SSLSession session) {
    try {
      final Certificate[] certs = session.getPeerCertificates();
      final X509Certificate x509 = (X509Certificate)certs[0];
      verify(host, x509);
      return true;
    }
    catch (final SSLException e) {
      return false;
    }
  }

  @Override
  public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
    // actually never used, because it's only used in verify(final String host, final X509Certificate cert)
    myVerifier.verify(host, cns, subjectAlts);
  }
}
