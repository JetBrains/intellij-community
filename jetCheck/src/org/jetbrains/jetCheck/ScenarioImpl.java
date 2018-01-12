/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class ScenarioImpl implements Scenario {
  private final List<Object> log = new ArrayList<>();
  private Throwable failure;

  ScenarioImpl(@NotNull ImperativeCommand cmd, @NotNull DataStructure data) {
    try {
      performCommand(cmd, data, log);
    }
    catch (Throwable e) {
      addFailure(e);
    }
    if (failure instanceof CannotRestoreValue) {
      throw (CannotRestoreValue)failure;
    }
  }

  private void addFailure(Throwable e) {
    if (failure == null) {
      failure = e;
    }
  }

  private void performCommand(ImperativeCommand command, DataStructure data, List<Object> log) {
    command.performCommand(new ImperativeCommand.Environment() {
      @Override
      public void logMessage(@NotNull String message) {
        log.add(message);
      }

      @Override
      public <T> T generateValue(@NotNull Generator<T> generator, @Nullable String logMessage) {
        T value = safeGenerate(data, generator);
        if (logMessage != null) {
          logMessage(String.format(logMessage, value));
        }
        return value;
      }

      @Override
      public void executeCommands(IntDistribution count, Generator<? extends ImperativeCommand> cmdGen) {
        innerCommandLists(Generator.listsOf(count, innerCommands(cmdGen)));
      }

      @Override
      public void executeCommands(Generator<? extends ImperativeCommand> cmdGen) {
        innerCommandLists(Generator.nonEmptyLists(innerCommands(cmdGen)));
      }

      private void innerCommandLists(final Generator<List<Object>> listGen) {
        data.generate(Generator.from(new EquivalentGenerator<List<Object>>() {
          @Override
          public List<Object> apply(DataStructure data) {
            return listGen.getGeneratorFunction().apply(data);
          }
        }));
      }

      @NotNull
      private Generator<Object> innerCommands(Generator<? extends ImperativeCommand> cmdGen) {
        return Generator.from(new EquivalentGenerator<Object>() {
          @Override
          public Object apply(DataStructure cmdData) {
            List<Object> localLog = new ArrayList<>();
            log.add(localLog);
            performCommand(safeGenerate(cmdData, cmdGen), cmdData, localLog);
            return null;
          }
        });
      }
    });
  }

  private <T> T safeGenerate(DataStructure data, Generator<T> generator) {
    try {
      return data.generate(generator);
    }
    catch (CannotRestoreValue e) { //todo test for evil intermediate code hiding this exception, also CannotSatisfyCondition
      addFailure(e);
      throw e;
    }
  }


  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof ScenarioImpl && log.equals(((ScenarioImpl)o).log);
  }

  @Override
  public int hashCode() {
    return log.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    printLog(sb, "", log);
    return "commands:" + (sb.length() == 0 ? "<none>" : sb.toString());
  }
  
  private static void printLog(StringBuilder sb, String indent, List<Object> log) {
    for (Object o : log) {
      if (o instanceof String) {
        sb.append("\n").append(indent).append(o);
      } else {
        //noinspection unchecked
        printLog(sb, indent + "  ", (List)o);
      }
    }
  }

  @Override
  public boolean ensureSuccessful() {
    if (failure instanceof Error) throw (Error)failure;
    if (failure instanceof RuntimeException) throw (RuntimeException)failure;
    if (failure != null) throw new RuntimeException(failure);
    return true;
  }
  
  private static abstract class EquivalentGenerator<T> implements Function<DataStructure, T> {
    @Override
    public boolean equals(Object obj) {
      return getClass() == obj.getClass(); // for recursive shrinking to work
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
    
  }

}
