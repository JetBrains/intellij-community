// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RollingFileHandler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

@ApiStatus.Internal
public final class IndexingEventsLogger {
  private static final boolean DEBUG = FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES || Boolean.getBoolean("log.index.vfs.events");
  private static final Logger LOG = MyLoggerFactory.getLoggerInstance();

  static {
    if (LOG != null) {
      LOG.info("-------------- VfsEventsMerger initialized --------------------");
    }
  }

  public static void tryLog(@NotNull String eventName, @NotNull VirtualFile file) {
    tryLog(eventName, file, null);
  }

  public static void tryLog(@NotNull String eventName, int fileId) {
    tryLog(() -> {
      return "e=" + eventName +
             ",id=" + fileId;
    });
  }

  public static void tryLog(@NotNull String eventName, @NotNull VirtualFile file, @Nullable Supplier<String> additionalMessage) {
    tryLog(() -> {
      return "e=" + eventName +
             (file instanceof VirtualFileWithId fileWithId ? (",id=" + fileWithId.getId()) : (",f=" + file.getPath())) +
             ",flen=" + file.getLength() +
             (additionalMessage == null ? "" : ("," + additionalMessage.get()));
    });
  }

  public static void tryLog(@NotNull String eventName, @NotNull IndexedFile indexedFile, @Nullable Supplier<String> additionalMessage) {
    VirtualFile file = indexedFile.getFile();

    tryLog(eventName, file, () -> {
      String extra = "f@" + System.identityHashCode(indexedFile);

      if (indexedFile instanceof FileContentImpl fileContentImpl) {
        extra += ",tr=" + (fileContentImpl.isTransientContent() ? "t" : "f");
      }

      if (indexedFile instanceof FileContent fileContent) {
        extra += ",contLen(b)=" + fileContent.getContent().length;
        FileType fileType = fileContent.getFileType();
        // WARNING: LanguageFileType does not guarantee that there is a PsiFile.
        // Example: org.jetbrains.bazel.languages.projectview.base.ProjectViewFileType
        // psiLen has never been helpful to me, so don't log it for now.
        // extra += ",psiLen=" + (fileType instanceof LanguageFileType ? fileContent.getPsiFile().getTextLength() : -1);
        extra += ",bin=" + (fileType.isBinary() ? "t" : "f");
      }

      if (additionalMessage != null) {
        extra += "," + additionalMessage.get();
      }
      return extra;
    });
  }

  public static void tryLog(Supplier<String> message) {
    if (LOG != null) {
      try {
        LOG.info(message.get());
      }
      catch (Throwable t) {
        Logger.getInstance(IndexingEventsLogger.class).error("Could not evaluate log message (message.get())", t);
      }
    }
  }

  private static final class MyLoggerFactory implements Logger.Factory {
    private static final @Nullable MyLoggerFactory ourFactory;

    static {
      MyLoggerFactory factory = null;
      try {
        if (DEBUG) {
          factory = new MyLoggerFactory();
        }
      }
      catch (IOException e) {
        ((FileBasedIndexEx)FileBasedIndex.getInstance()).getLogger().error(e);
      }
      ourFactory = factory;
    }

    private final @NotNull RollingFileHandler myAppender;

    MyLoggerFactory() throws IOException {
      Path indexingDiagnosticDir = Paths.get(PathManager.getLogPath()).resolve("indexing-diagnostic");
      Path logPath = indexingDiagnosticDir.resolve("index-vfs-events.log");
      myAppender = new RollingFileHandler(logPath, 20000000, 50, false);
      myAppender.setFormatter(new Formatter() {
        @Override
        public String format(LogRecord record) {
          ZonedDateTime zdt = ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault());
          return String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL [%3$d] %2$s%n", zdt, record.getMessage(),
                               record.getLongThreadID());
        }
      });
    }

    @Override
    public @NotNull Logger getLoggerInstance(@NotNull String category) {
      final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(category);
      JulLogger.clearHandlers(logger);
      logger.addHandler(myAppender);
      logger.setUseParentHandlers(false);
      logger.setLevel(Level.INFO);
      return new JulLogger(logger);
    }

    public static @Nullable Logger getLoggerInstance() {
      // we keep VfsEventsMerger for backward compatibility
      return ourFactory == null ? null : ourFactory.getLoggerInstance("#" + VfsEventsMerger.class.getName());
    }
  }
}
