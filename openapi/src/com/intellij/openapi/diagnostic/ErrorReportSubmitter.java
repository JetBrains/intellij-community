package com.intellij.openapi.diagnostic;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 17, 2005
 * Time: 9:11:44 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ErrorReportSubmitter {
  String getReportActionText();
  SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parentComponent);
}
