// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface LogConsumer {
  LogConsumer EMPTY = new LogConsumer() {
    @Override
    public void consume(Supplier<String> message) {
    }

    @Override
    public void consume(String message) {
    }
  };

  void consume(Supplier<String> message);

  void consume(String message);


  static LogConsumer composite(LogConsumer first, LogConsumer second) {
    return first == EMPTY? second : second == EMPTY? first : new LogConsumer() {
      @Override
      public void consume(Supplier<String> message) {
        try {
          first.consume(message);
        }
        finally {
          second.consume(message);
        }
      }

      @Override
      public void consume(String message) {
        try {
          first.consume(message);
        }
        finally {
          second.consume(message);
        }
      }
    };
  }

  static LogConsumer memory(StringBuilder builder) {
    return new LogConsumer() {
      @Override
      public void consume(Supplier<String> message) {
        builder.append(message.get()).append("\n");
      }

      @Override
      public void consume(String message) {
        builder.append(message).append("\n");
      }
    };
  }

  static LogConsumer createJULogConsumer(Level level) {
    return createJULogConsumer("#org.jetbrains.jps.dependency.java.JvmDifferentiateStrategy", level);
  }
  
  static LogConsumer createJULogConsumer(String category, Level level) {
    Logger logger = Logger.getLogger(category);
    return new LogConsumer() {
      @Override
      public void consume(Supplier<String> message) {
        logger.log(level, message);
      }

      @Override
      public void consume(String message) {
        logger.log(level, message);
      }
    };
  }
}
