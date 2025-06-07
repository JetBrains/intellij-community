package com.intellij.database.dbimport;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public abstract class TypeMerger {
  private final @NotNull String myName;
  private final @NotNull Consumer<String> myConsumer;
  private final int myPriority;

  TypeMerger(@NotNull String name, @NotNull Consumer<String> consumer, int priority) {
    myName = name;
    myConsumer = consumer;
    myPriority = priority;
  }

  public int getPriority() {
    return myPriority;
  }

  public @NotNull String getName() {
    return myName;
  }

  public boolean isSuitable(@NotNull String s) {
    try {
      myConsumer.consume(s);
      return true;
    }
    catch (Exception ignore) {
    }
    return false;
  }

  public TypeMerger merge(@NotNull TypeMerger toMerge) {
    int priority = toMerge.getPriority();
    if (getPriority() > priority) return this;
    return toMerge;
  }

  public static class StringMerger extends TypeMerger {
    public StringMerger(@NotNull String name) {
      super(name, s -> {}, 4);
    }
  }

  public static class DoubleMerger extends TypeMerger {
    public DoubleMerger(@NotNull String name) {
      //noinspection ResultOfMethodCallIgnored
      super(name, Double::valueOf, 2);
    }
  }

  public static class BigIntegerMerger extends TypeMerger {
    public BigIntegerMerger(@NotNull String name) {
      super(name, s -> new BigInteger(s), 1);
    }
  }

  public static class IntegerMerger extends TypeMerger {
    public IntegerMerger(@NotNull String name) {
      //noinspection ResultOfMethodCallIgnored
      super(name, Integer::valueOf, 0);
    }
  }

  public static class BooleanMerger extends TypeMerger {
    public BooleanMerger(@NotNull String name) {
      super(name, v -> {
        if (!StringUtil.equalsIgnoreCase(v, "true") && !StringUtil.equalsIgnoreCase(v, "false")) {
          throw new IllegalArgumentException();
        }
      }, 0);
    }
  }
}
