// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.auth;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;

/**
 * Post to {@link PluginRepositoryAuthListener#PLUGIN_REPO_AUTH_CHANGED_TOPIC} to invalidate custom plugin repo caches
 * @see {@link PluginRepositoryAuthProvider}
 */
public interface PluginRepositoryAuthListener {

  Topic<PluginRepositoryAuthListener> PLUGIN_REPO_AUTH_CHANGED_TOPIC =
    Topic.create("plugin repo auth changed", PluginRepositoryAuthListener.class);

  void authenticationChanged();

  static void notifyAuthChanged() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(PLUGIN_REPO_AUTH_CHANGED_TOPIC).authenticationChanged();
  }
}
