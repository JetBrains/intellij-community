/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
   * @return "Report to vendor" action text to be used in Error Reporter user interface. For example: "Report to JetBrains".
   */
  public abstract String getReportActionText();

  /**
   * This method is called whenever fatal error (aka exception) in plugin code had happened and user decided to report this problem to
   * plugin vendor.
   * @param events sequence of the fatal error descriptors. Fatal errors that happened immediately one after another most probably caused
   * by first one that happened so it's a common practice to submit only first one. Array passed is guaranteed to have at least one element.
   * @param parentComponent one usually wants to show up a dialog asking user for additional details and probably authentication info.
   * parentComponent parameter is passed so dialog that would come up would be properly aligned with its parent dialog (IDE Fatal Errors).
   * @return submission result status.
   */
  public abstract SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parentComponent);

  public void submitAsync(IdeaLoggingEvent[] events,
                          String additionalInfo,
                          Component parentComponent,
                          Consumer<SubmittedReportInfo> consumer) {
    consumer.consume(submit(events, parentComponent));
  }

  public boolean trySubmitAsync(IdeaLoggingEvent[] events,
                                String additionalInfo,
                                Component parentComponent,
                                Consumer<SubmittedReportInfo> consumer) {
    submitAsync(events, additionalInfo, parentComponent, consumer);
    return true;
  }
}
