// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.NonStaticAuthenticator;
import org.jetbrains.annotations.NotNull;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * @deprecated use {@link JdkProxyProvider#getAuthenticator()} or {@link IdeProxyAuthenticator}
 */
@SuppressWarnings("removal")
@Deprecated
public final class IdeaWideAuthenticator extends NonStaticAuthenticator {
  private static final Logger LOG = Logger.getInstance(IdeaWideAuthenticator.class);
  private final HttpConfigurable myHttpConfigurable;

  public IdeaWideAuthenticator(@NotNull HttpConfigurable configurable) {
    myHttpConfigurable = configurable;
  }

  @Override
  public synchronized PasswordAuthentication getPasswordAuthentication() {
    final String host = CommonProxy.getHostNameReliably(getRequestingHost(), getRequestingSite(), getRequestingURL());
    // java.base/java/net/SocksSocketImpl.java:176 : there is SOCKS proxy auth, but without RequestorType passing
    final boolean isProxy = Authenticator.RequestorType.PROXY.equals(getRequestorType()) || "SOCKS authentication".equals(getRequestingPrompt());
    // FIXME prefix for server auth is never used since 7ea74ea400b03cf92d1621ea0e8aa1d386cb886a.
    //  This means that this class manages strictly proxy authentication since 2013
    final String prefix = isProxy ? IdeBundle.message("prompt.proxy.authentication") : IdeBundle.message("prompt.server.authentication");
    Application application = ApplicationManager.getApplication();
    if (isProxy) {
      // according to idea-wide settings
      if (myHttpConfigurable.USE_HTTP_PROXY) {
        LOG.debug("CommonAuthenticator.getPasswordAuthentication will return common defined proxy");
        return myHttpConfigurable.getPromptedAuthentication(host + ":" + getRequestingPort(), getRequestingPrompt());
      }
      else if (myHttpConfigurable.USE_PROXY_PAC) {
        LOG.debug("CommonAuthenticator.getPasswordAuthentication will return autodetected proxy");
        if (myHttpConfigurable.isGenericPasswordCanceled(host, getRequestingPort())) return null;
        // same but without remembering the results..
        final PasswordAuthentication password = myHttpConfigurable.getGenericPassword(host, getRequestingPort());
        if (password != null) {
          return password;
        }
        // do not try to show any dialogs if application is exiting
        if (application == null || application.isDisposed()) {
          return null;
        }

        return myHttpConfigurable.getGenericPromptedAuthentication(prefix, host, getRequestingPrompt(), getRequestingPort(), true);
      }
    }

    // FIXME dead logic, all ends up with return null
    // do not try to show any dialogs if application is exiting
    if (application == null || application.isDisposed()) {
      return null;
    }

    LOG.debug("CommonAuthenticator.getPasswordAuthentication generic authentication will be asked");
    //return myHttpConfigurable.getGenericPromptedAuthentication(prefix, host, getRequestingPrompt(), getRequestingPort(), false);
    return null;
  }
}
