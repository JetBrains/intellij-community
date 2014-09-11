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
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * This class should be extended by plugin vendor and provided by means of {@link com.intellij.ExtensionPoints#ERROR_HANDLER} if
 * reporting errors that happened in plugin code to vendor is desirable.
 */
public abstract class ErrorReportSubmitter implements PluginAware {
  private PluginDescriptor myPlugin;

  /**
   * Called by the framework. Allows to identify the plugin that provided this extension.
   */
  @Override
  public void setPluginDescriptor(PluginDescriptor plugin) {
    myPlugin = plugin;
  }

  /**
   * @return plugin that provided this particular extension
   */
  public PluginDescriptor getPluginDescriptor() {
    return myPlugin;
  }

  /**
   * @return an action text to be used in Error Reporter user interface, e.g. "Report to JetBrains".
   */
  public abstract String getReportActionText();

  /**
   * This method is called whenever an exception in a plugin code had happened and a user decided to report a problem to the plugin vendor.
   *
   * @param events          a non-empty sequence of error descriptors.
   * @param additionalInfo  additional information provided by a user.
   * @param parentComponent UI component to use as a parent in any UI activity from a submitter.
   * @param consumer        a callback to be called after sending is finished (or failed).
   * @return {@code true} if reporting was started, {@code false} if a report can't be sent at the moment.
   */
  @SuppressWarnings("deprecation")
  public boolean submit(@NotNull IdeaLoggingEvent[] events,
                        @Nullable String additionalInfo,
                        @NotNull Component parentComponent,
                        @NotNull Consumer<SubmittedReportInfo> consumer) {
    return trySubmitAsync(events, additionalInfo, parentComponent, consumer);
  }

  /** @deprecated implement {@link #submit(IdeaLoggingEvent[], String, Component, Consumer)} (to be removed in IDEA 16) */
  @SuppressWarnings({"deprecation", "unused"})
  public boolean trySubmitAsync(IdeaLoggingEvent[] events, String info, Component parent, Consumer<SubmittedReportInfo> consumer) {
    submitAsync(events, info, parent, consumer);
    return true;
  }

  /** @deprecated implement {@link #submit(IdeaLoggingEvent[], String, Component, Consumer)} (to be removed in IDEA 16) */
  @SuppressWarnings({"deprecation", "unused"})
  public void submitAsync(IdeaLoggingEvent[] events, String info, Component parent, Consumer<SubmittedReportInfo> consumer) {
    consumer.consume(submit(events, parent));
  }

  /** @deprecated implement {@link #submit(IdeaLoggingEvent[], String, Component, Consumer)} (to be removed in IDEA 16) */
  @SuppressWarnings({"deprecation", "unused"})
  public SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parent) {
    throw new UnsupportedOperationException("Deprecated API called");
  }
}
