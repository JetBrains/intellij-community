package com.intellij.openapi.diagnostic;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 17, 2005
 * Time: 9:11:44 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ErrorReportSubmitter implements PluginAware {
  public final static String ERROR_HANDLER_EXTENSION_POINT = "com.intellij.errorHandler";
  private PluginDescriptor myPlugin;

  public void setPluginDescriptor(PluginDescriptor plugin) {
    myPlugin = plugin;
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPlugin;
  }

  public abstract String getReportActionText();
  public abstract SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parentComponent);
}
