// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public abstract String getReportActionText();

  /**
   * @return a text of a privacy notice to be shown in the dialog (in HTML; links are allowed).
   */
  public String getPrivacyNoticeText() {
    return null;
  }

  /**
   * This method is called whenever an exception in a plugin code had happened and a user decided to report a problem to the plugin vendor.
   *
   * @param events          a non-empty sequence of error descriptors.
   * @param additionalInfo  additional information provided by a user.
   * @param parentComponent UI component to use as a parent in any UI activity from a submitter.
   * @param consumer        a callback to be called after sending is finished (or failed).
   * @return {@code true} if reporting was started, {@code false} if a report can't be sent at the moment.
   */
  public boolean submit(@NotNull IdeaLoggingEvent[] events,
                        @Nullable String additionalInfo,
                        @NotNull Component parentComponent,
                        @NotNull Consumer<SubmittedReportInfo> consumer) {
    //noinspection deprecation
    return trySubmitAsync(events, additionalInfo, parentComponent, consumer);
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated implement {@link #submit(IdeaLoggingEvent[], String, Component, Consumer)} instead (to be removed in IDEA 2019) */
  @Deprecated
  @SuppressWarnings("ALL")
  public boolean trySubmitAsync(IdeaLoggingEvent[] events, String info, Component parent, Consumer<SubmittedReportInfo> consumer) {
    submitAsync(events, info, parent, consumer);
    return true;
  }

  /** @deprecated implement {@link #submit(IdeaLoggingEvent[], String, Component, Consumer)} instead (to be removed in IDEA 2019) */
  @Deprecated
  @SuppressWarnings("ALL")
  public void submitAsync(IdeaLoggingEvent[] events, String info, Component parent, Consumer<SubmittedReportInfo> consumer) {
    consumer.consume(submit(events, parent));
  }

  /** @deprecated implement {@link #submit(IdeaLoggingEvent[], String, Component, Consumer)} instead (to be removed in IDEA 2019) */
  @Deprecated
  @SuppressWarnings("ALL")
  public SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parent) {
    throw new UnsupportedOperationException("Deprecated API called");
  }
  //</editor-fold>
}