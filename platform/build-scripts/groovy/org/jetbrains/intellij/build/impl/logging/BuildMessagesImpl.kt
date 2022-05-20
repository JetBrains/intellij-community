// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging;

import com.intellij.util.containers.Stack;
import com.intellij.util.text.UniqueNameGenerator;
import groovy.lang.Closure;
import groovy.lang.Reference;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadableSpan;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.jetbrains.intellij.build.*;
import org.jetbrains.intellij.build.impl.LayoutBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class BuildMessagesImpl implements BuildMessages {
  @Override
  public String getName() {
    return "";
  }

  @Override
  public boolean isLoggable(Level level) {
    return level.getSeverity() > Level.TRACE.getSeverity();
  }

  @Override
  public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
    if (level.equals(Level.INFO)) {
      assert thrown == null;
      info(message);
    }
    else if (level.equals(Level.ERROR)) {
      error(message, thrown);
    }
    else if (level.equals(Level.WARNING)) {
      assert thrown == null;
      warning(message);
    }
    else {
      assert thrown == null;
      debug(message);
    }
  }

  @Override
  public void log(Level level, ResourceBundle bundle, String message, Object... params) {
    log(level, null, message, (Throwable)null);
  }

  @Override
  public void log(Level level, String message, Object... params) {
    log(level, null, message, (Throwable)null);
  }

  @Override
  public void log(Level level, String message) {
    log(level, null, message, (Throwable)null);
  }

  @Override
  public void log(Level level, String message, Throwable thrown) {
    log(level, null, message, thrown);
  }

  public static BuildMessagesImpl create() {
    Project antProject = LayoutBuilder.getAnt().getProject();

    String key = "IntelliJBuildMessages";
    Object registered = antProject.getReference(key);
    if (registered != null) return DefaultGroovyMethods.asType(registered, BuildMessagesImpl.class);

    boolean underTeamCity = System.getenv("TEAMCITY_VERSION") != null;
    disableAntLogging(antProject);
    final Reference<BiFunction<String, AntTaskLogger, BuildMessageLogger>> mainLoggerFactory;
    if (underTeamCity) {
      mainLoggerFactory.set(TeamCityBuildMessageLogger.FACTORY);
    }
    else {
      mainLoggerFactory.set(ConsoleBuildMessageLogger.FACTORY);
    }

    final DebugLogger debugLogger = new DebugLogger();
    BiFunction<String, AntTaskLogger, BuildMessageLogger> loggerFactory = (taskName, logger) -> {
      return new CompositeBuildMessageLogger(List.of(mainLoggerFactory.get().apply(taskName, logger), debugLogger.createLogger(taskName)));
    };
    AntTaskLogger antTaskLogger = new AntTaskLogger(antProject);
    BuildMessagesImpl messages =
      new BuildMessagesImpl(loggerFactory.apply(null, antTaskLogger), loggerFactory, antTaskLogger, debugLogger, null);
    antTaskLogger.setDefaultHandler(messages);
    antProject.addBuildListener(antTaskLogger);
    antProject.addReference(key, messages);
    return ((BuildMessagesImpl)(messages));
  }

  /**
   * default Ant logging doesn't work well with parallel tasks, so we use our own {@link AntTaskLogger} instead
   */
  private static void disableAntLogging(Project project) {
    DefaultGroovyMethods.each(project.getBuildListeners(), new Closure<Void>(null, null) {
      public void doCall(BuildListener it) {
        if (it instanceof DefaultLogger) {
          ((DefaultLogger)it).setMessageOutputLevel(Project.MSG_ERR);
        }
      }

      public void doCall() {
        doCall(null);
      }
    });
  }

  private BuildMessagesImpl(BuildMessageLogger logger,
                            BiFunction<String, AntTaskLogger, BuildMessageLogger> loggerFactory,
                            AntTaskLogger antTaskLogger,
                            DebugLogger debugLogger,
                            BuildMessagesImpl parentInstance) {
    this.logger = logger;
    this.loggerFactory = loggerFactory;
    this.antTaskLogger = antTaskLogger;
    this.debugLogger = debugLogger;
    this.parentInstance = parentInstance;
  }

  @Override
  public void info(String message) {
    processMessage(new LogMessage(LogMessage.Kind.INFO, message));
  }

  @Override
  public void warning(String message) {
    processMessage(new LogMessage(LogMessage.Kind.WARNING, message));
  }

  @Override
  public void debug(String message) {
    processMessage(new LogMessage(LogMessage.Kind.DEBUG, message));
  }

  public void setDebugLogPath(Path path) {
    debugLogger.setOutputFile(path);
  }

  public Path getDebugLogFile() {
    return debugLogger.getOutputFile();
  }

  @Override
  public void error(String message) {
    try {
      TraceManager.INSTANCE.finish();
    }
    catch (Throwable e) {
      System.err.println("Cannot finish tracing: " + e);
    }

    throw new BuildException(message);
  }

  @Override
  public void error(String message, final Throwable cause) {
    StringWriter writer = new StringWriter();
    IOGroovyMethods.withCloseable(new PrintWriter(writer), new Closure<Void>(this, this) {
      public Void doCall(PrintWriter it) { return cause.printStackTrace(it); }

      public Void doCall() {
        return doCall(null);
      }
    });
    processMessage(new LogMessage(LogMessage.Kind.ERROR, message + "\n" + String.valueOf(writer)));
    throw new BuildException(message, cause);
  }

  @Override
  public void compilationError(String compilerName, String message) {
    compilationErrors(compilerName, new ArrayList<String>(Arrays.asList(message)));
  }

  @Override
  public void compilationErrors(String compilerName, List<String> messages) {
    processMessage(new CompilationErrorsLogMessage(compilerName, messages));
  }

  @Override
  public void progress(String message) {
    if (parentInstance != null) {
      //progress messages should be shown immediately, there are no problems with that since they aren't organized into groups
      parentInstance.progress(message);
    }
    else {
      logger.processMessage(new LogMessage(LogMessage.Kind.PROGRESS, message));
    }
  }

  @Override
  public void buildStatus(String message) {
    processMessage(new LogMessage(LogMessage.Kind.BUILD_STATUS, message));
  }

  @Override
  public void setParameter(String parameterName, String value) {
    processMessage(new LogMessage(LogMessage.Kind.SET_PARAMETER, parameterName + "=" + value));
  }

  @Override
  public <V> V block(String blockName, Supplier<V> body) {
    return block(TraceManager.spanBuilder(blockName.toLowerCase()), body);
  }

  @Override
  public <V> V block(SpanBuilder spanBuilder, Supplier<V> body) {
    Span span = spanBuilder.startSpan();
    Scope scope = span.makeCurrent();
    String blockName = ((ReadableSpan)span).getName();
    try {
      blockNames.push(blockName);
      processMessage(new LogMessage(LogMessage.Kind.BLOCK_STARTED, blockName));
      return body.get();
    }
    catch (IntelliJBuildException e) {
      span.setStatus(StatusCode.ERROR, e.getMessage());
      span.recordException(e);
      throw e;
    }
    catch (BuildException e) {
      span.setStatus(StatusCode.ERROR, e.getMessage());
      span.recordException(e);
      throw new IntelliJBuildException(DefaultGroovyMethods.join(blockNames, " > "), e.getMessage(), e);
    }
    catch (Throwable e) {
      if (e instanceof UndeclaredThrowableException) {
        e = e.getCause();
      }


      span.recordException(e);
      span.setStatus(StatusCode.ERROR, e.getMessage());

      // print all pending spans
      TracerProviderManager.INSTANCE.flush();
      throw e;
    }
    finally {
      try {
        scope.close();
      }
      finally {
        span.end();
      }


      blockNames.pop();
      processMessage(new LogMessage(LogMessage.Kind.BLOCK_FINISHED, blockName));
    }
  }

  @Override
  public void artifactBuilt(String relativeArtifactPath) {
    logger.processMessage(new LogMessage(LogMessage.Kind.ARTIFACT_BUILT, relativeArtifactPath));
  }

  @Override
  public void reportStatisticValue(String key, String value) {
    processMessage(new LogMessage(LogMessage.Kind.STATISTICS, key + "=" + value));
  }

  public void processMessage(LogMessage message) {
    if (parentInstance != null) {
      //It appears that TeamCity currently cannot properly handle log messages from parallel tasks (https://youtrack.jetbrains.com/issue/TW-46515)
      //Until it is fixed we need to delay delivering of messages from the tasks running in parallel until all tasks have been finished.
      delayedMessages.add(message);
    }
    else {
      logger.processMessage(message);
    }
  }

  @Override
  public BuildMessages forkForParallelTask(String suggestedTaskName) {
    String taskName = taskNameGenerator.generateUniqueName(suggestedTaskName);
    BuildMessagesImpl forked =
      new BuildMessagesImpl(loggerFactory.apply(taskName, antTaskLogger), loggerFactory, antTaskLogger, debugLogger, this);
    DefaultGroovyMethods.leftShift(forkedInstances, forked);
    return ((BuildMessages)(forked));
  }

  @Override
  public void onAllForksFinished() {
    DefaultGroovyMethods.each(forkedInstances, new Closure<Void>(this, this) {
      public void doCall(final Object forked) {
        DefaultGroovyMethods.each(forked.delayedMessages, new Closure<Void>(BuildMessagesImpl.this, BuildMessagesImpl.this) {
          public void doCall(LogMessage it) {
            forked.logger.processMessage(it);
          }

          public void doCall() {
            doCall(null);
          }
        });
        forked.logger.dispose();
      }
    });
    forkedInstances.clear();
  }

  @Override
  public void onForkStarted() {
    antTaskLogger.registerThreadHandler(Thread.currentThread(), this);
  }

  @Override
  public void onForkFinished() {
    antTaskLogger.unregisterThreadHandler(Thread.currentThread());
  }

  private final BuildMessageLogger logger;
  private final BiFunction<String, AntTaskLogger, BuildMessageLogger> loggerFactory;
  private final AntTaskLogger antTaskLogger;
  private final DebugLogger debugLogger;
  private final BuildMessagesImpl parentInstance;
  private final List<BuildMessagesImpl> forkedInstances = new ArrayList<BuildMessagesImpl>();
  private final List<LogMessage> delayedMessages = new ArrayList<LogMessage>();
  private final UniqueNameGenerator taskNameGenerator = new UniqueNameGenerator();
  private final Stack<String> blockNames = new Stack<String>();
}
