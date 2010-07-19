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
package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class DialogAppender extends AppenderSkeleton {
  private static final DefaultIdeaErrorLogger DEFAULT_LOGGER = new DefaultIdeaErrorLogger();

  private Runnable myDialogRunnable = null;

  protected synchronized void append(final LoggingEvent event) {
    if (!event.level.isGreaterOrEqual(Priority.ERROR)) return;

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        List<ErrorLogger> loggers = new ArrayList<ErrorLogger>();
        loggers.add(DEFAULT_LOGGER);

        Application application = ApplicationManager.getApplication();
        if (application != null) {
          if (application.isHeadlessEnvironment() || application.isDisposed()) return;
          ContainerUtil.addAll(loggers, application.getComponents(ErrorLogger.class));
        }

        appendToLoggers(event, loggers.toArray(new ErrorLogger[loggers.size()]));
      }
    });
  }

  void appendToLoggers(final LoggingEvent event, ErrorLogger[] errorLoggers) {
    if (myDialogRunnable != null) {
      return;
    }

    ThrowableInformation throwable = event.getThrowableInformation();
    if (throwable == null) {
      return;
    }

    final Object message = event.getMessage();
    final IdeaLoggingEvent ideaEvent = new IdeaLoggingEvent(message == null ? "<null> " : message.toString(), throwable.getThrowable());
    for (int i = errorLoggers.length - 1; i >= 0; i--) {

      final ErrorLogger logger = errorLoggers[i];
      if (logger.canHandle(ideaEvent)) {

        myDialogRunnable = new Runnable() {
          public void run() {
            try {
              logger.handle(ideaEvent);
            }
            finally {
              myDialogRunnable = null;
            }
          }
        };

        final Application app = ApplicationManager.getApplication();
        if (app == null) {
          new Thread(myDialogRunnable).start();  
        } else {
          app.executeOnPooledThread(myDialogRunnable);
        }

        break;
      }
    }
  }

  public boolean requiresLayout() {
    return false;
  }

  public void close() {
  }
}

