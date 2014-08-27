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
package com.intellij.util.net;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.NonStaticAuthenticator;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class IdeaWideAuthenticator extends NonStaticAuthenticator {
  private final static Logger LOG = Logger.getInstance("#com.intellij.util.net.IdeaWideAuthenticator");
  private final HttpConfigurable myHttpConfigurable;

  public IdeaWideAuthenticator(HttpConfigurable configurable) {
    myHttpConfigurable = configurable;
  }

  @Override
  public PasswordAuthentication getPasswordAuthentication() {
    final String host = CommonProxy.getHostNameReliably(getRequestingHost(), getRequestingSite(), getRequestingURL());
    final boolean isProxy = Authenticator.RequestorType.PROXY.equals(getRequestorType());
    final String prefix = isProxy ? "Proxy authentication: " : "Server authentication: ";
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
        if (application == null || application.isDisposeInProgress() ||
            application.isDisposed()) {
          return null;
        }

        return myHttpConfigurable.getGenericPromptedAuthentication(prefix, host, getRequestingPrompt(), getRequestingPort(), true);
      }
    }

    // do not try to show any dialogs if application is exiting
    if (application == null || application.isDisposeInProgress() || application.isDisposed()) {
      return null;
    }

    LOG.debug("CommonAuthenticator.getPasswordAuthentication generic authentication will be asked");
    //return myHttpConfigurable.getGenericPromptedAuthentication(prefix, host, getRequestingPrompt(), getRequestingPort(), false);
    return null;
  }
}
