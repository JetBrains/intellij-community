// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.remote;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;

public class IR {
  public interface RemoteRunner {
    //static Process runSimpleProcess(String commandLine, ProgressIndicator indicator) {
    //  return null;
    //}

    @NotNull
    RemotePlatform getRemotePlatform();

    RemoteEnvironmentRequest createRequest();

    RemoteEnvironment prepareRemoteEnvironment(@NotNull RemoteEnvironmentRequest request, @NotNull ProgressIndicator indicator);
  }

  public interface RemoteEnvironment {
    RemotePlatform getRemotePlatform();

    Process createProcess(NewCommandLine commandLine, ProgressIndicator indicator) throws ExecutionException;
  }

  public interface RemoteEnvironmentRequest {
    RemotePlatform getRemotePlatform();

    /**
     * Runtimes must perform uploads in the order they were created
     * 
     * todo[remoteServers]: change to Path
     */
    RemoteValue<String> createUpload(@NotNull String localPath);

    RemoteValue<Integer> bindRemotePort(int remotePort);

    RemoteLocation createRemoteLocation(@Nullable String remotePath/*, @Nullable Object __options*/);

    RemoteLocation importLocation(RemoteLocation otherLoc, RemoteEnvironment originalEnv);

    void addValueResolutionListener(@NotNull RemoveValueResolutionListener listener);

    void removeValueResolutionListener(@NotNull RemoveValueResolutionListener listener);
  }

  public interface RemoteValue<T> {
    @SuppressWarnings("rawtypes")
    RemoteValue EMPTY_VALUE = new RemoteValue() {
      @Override
      public Object getLocalValue() {
        return null;
      }

      @Override
      public Object getRemoteValue() {
        return null;
      }
    };

    static <V> RemoteValue<V> EMPTY_VALUE() {
      //noinspection unchecked
      return EMPTY_VALUE;
    }

    //todo[remoteServers]: rename? it's easy to accidentally use toString() instead of toString(env)
    //@Nullable
    //String toString(@NotNull RemoteEnvironment environment);

    T getLocalValue();

    T getRemoteValue();
  }

  public interface RemoteLocation {
    void dispose();

    String getRemotePath();

    //Path download(ProgressIndicator indicator);
  }

  public static class FixedValue<T> implements RemoteValue<T> {
    private final T myValue;

    public FixedValue(@NotNull T value) {
      myValue = value;
    }

    @Override
    public T getRemoteValue() {
      return myValue;
    }

    @Override
    public T getLocalValue() {
      return myValue;
    }
  }

  public static class CompositeValue<S, T> implements RemoteValue<T> {
    @NotNull private final Collection<RemoteValue<S>> myValues;
    @NotNull private final Function<Collection<S>, T> myMapper;

    public CompositeValue(@NotNull Collection<RemoteValue<S>> values, @NotNull Function<Collection<S>, T> mapper) {
      myValues = values;
      myMapper = mapper;
    }

    @Override
    public T getLocalValue() {
      return myMapper.apply(ContainerUtil.map(myValues, RemoteValue::getLocalValue));
    }

    @Override
    public T getRemoteValue() {
      return myMapper.apply(ContainerUtil.map(myValues, RemoteValue::getRemoteValue));
    }
  }

  public static class MapValue<S, T> implements RemoteValue<T> {
    @NotNull private final RemoteValue<? extends S> myOriginalValue;
    @NotNull private final Function<S, T> myMapper;

    public MapValue(@NotNull RemoteValue<? extends S> originalValue, @NotNull Function<S, T> mapper) {
      myOriginalValue = originalValue;
      myMapper = mapper;
    }

    @Override
    public T getLocalValue() {
      return myMapper.apply(myOriginalValue.getLocalValue());
    }

    @Override
    public T getRemoteValue() {
      return myMapper.apply(myOriginalValue.getRemoteValue());
    }
  }

  public static class NewCommandLine {
    private RemoteValue<String> myExePath = RemoteValue.EMPTY_VALUE();
    private RemoteValue<String> myWorkingDirectory = RemoteValue.EMPTY_VALUE();
    private RemoteValue<String> myInputFilePath = RemoteValue.EMPTY_VALUE();
    private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();

    private final List<RemoteValue<String>> myParameters = new ArrayList<>();
    private final Map<String, RemoteValue<String>> myEnvironment = new HashMap<>();

    /**
     * {@link GeneralCommandLine#getPreparedCommandLine()}
     */
    public List<String> prepareCommandLine(@NotNull RemoteEnvironment target) {
      String command = myExePath.getRemoteValue();
      if (command == null) {
        // todo[remoteServers]: handle this properly
        throw new RuntimeException("Cannot find command");
      }
      return CommandLineUtil.toCommandLine(command, getParameters(), target.getRemotePlatform().getPlatform());
    }

    public void setCharset(@NotNull Charset charset) {
      myCharset = charset;
    }

    public void setExePath(@NotNull RemoteValue<String> exePath) {
      myExePath = exePath;
    }

    public void setExePath(@NotNull String exePath) {
      myExePath = new FixedValue<>(exePath);
    }

    public void setWorkingDirectory(@NotNull RemoteValue<String> workingDirectory) {
      myWorkingDirectory = workingDirectory;
    }

    public void addParameter(@NotNull RemoteValue<String> parameter) {
      myParameters.add(parameter);
    }

    public void addParameter(@NotNull String parameter) {
      myParameters.add(new FixedValue<>(parameter));
    }

    public void addEnvironmentVariable(String name, RemoteValue<String> value) {
      myEnvironment.put(name, value);
    }

    public void addEnvironmentVariable(String name, String value) {
      myEnvironment.put(name, new FixedValue<>(value));
    }

    public void setInputFile(@NotNull RemoteValue<String> inputFilePath) {
      myInputFilePath = inputFilePath;
    }

    public String getExePath() {
      return myExePath.getRemoteValue();
    }

    @Nullable
    public String getWorkingDirectory() {
      return myWorkingDirectory.getRemoteValue();
    }

    @Nullable
    public String getInputFilePath() {
      return myInputFilePath.getRemoteValue();
    }

    @NotNull
    public List<String> getParameters() {
      return ContainerUtil.mapNotNull(myParameters, RemoteValue::getRemoteValue);
    }

    @NotNull
    public Map<String, String> getEnvironmentVariables() {
      return ContainerUtil.map2MapNotNull(myEnvironment.entrySet(), e -> {
        String value = e.getValue().getRemoteValue();
        return value != null ? Pair.create(e.getKey(), value) : null;
      });
    }

    @NotNull
    public Charset getCharset() {
      return myCharset;
    }

    public void addParameters(@NotNull List<String> parametersList) {
      for (String parameter : parametersList) {
        addParameter(parameter);
      }
    }
  }

  public interface RemoveValueResolutionListener {
    default void beforeResolution(@NotNull RemoteValue<?> value) {}

    default void afterResolution(@NotNull RemoteValue<?> value) {}
  }

  public static class LocalRunner implements RemoteRunner {

    @NotNull
    @Override
    public RemotePlatform getRemotePlatform() {
      return RemotePlatform.CURRENT;
    }

    @Override
    public RemoteEnvironmentRequest createRequest() {
      return new LocalEnvironmentRequest();
    }

    @Override
    public LocalRemoteEnvironment prepareRemoteEnvironment(@NotNull RemoteEnvironmentRequest request,
                                                           @NotNull ProgressIndicator indicator) {
      return new LocalRemoteEnvironment(request);
    }

    public class LocalEnvironmentRequest implements RemoteEnvironmentRequest {
      @NotNull
      private final Collection<RemoveValueResolutionListener> myListeners = new SmartList<>();
      @NotNull
      private GeneralCommandLine.ParentEnvironmentType myParentEnvironmentType = GeneralCommandLine.ParentEnvironmentType.CONSOLE;

      @Override
      public RemotePlatform getRemotePlatform() {
        return LocalRunner.this.getRemotePlatform();
      }

      @Override
      public RemoteValue<String> createUpload(@NotNull String localPath) {
        FixedValue<String> value = new FixedValue<>(localPath);
        for (RemoveValueResolutionListener listener : myListeners) {
          listener.beforeResolution(value);
          listener.afterResolution(value);
        }
        return value;
      }

      @Override
      public RemoteValue<Integer> bindRemotePort(int remotePort) {
        FixedValue<Integer> value = new FixedValue<>(remotePort);
        for (RemoveValueResolutionListener listener : myListeners) {
          listener.beforeResolution(value);
          listener.afterResolution(value);
        }
        return value;
      }

      @Override
      public RemoteLocation createRemoteLocation(@Nullable String remotePath) {
        return null;
      }

      @Override
      public RemoteLocation importLocation(RemoteLocation otherLoc, RemoteEnvironment originalEnv) {
        return null;
      }

      public void setParentEnvironmentType(@NotNull GeneralCommandLine.ParentEnvironmentType parentEnvironmentType) {
        myParentEnvironmentType = parentEnvironmentType;
      }

      @Override
      public void addValueResolutionListener(@NotNull RemoveValueResolutionListener listener) {
        myListeners.add(listener);
      }

      @Override
      public void removeValueResolutionListener(@NotNull RemoveValueResolutionListener listener) {
        myListeners.remove(listener);
      }
    }
  }

  public static class LocalRemoteEnvironment implements RemoteEnvironment {
    @NotNull
    private final RemoteEnvironmentRequest myRequest;

    public LocalRemoteEnvironment(@NotNull RemoteEnvironmentRequest request) {
      myRequest = request;
    }

    @Override
    public RemotePlatform getRemotePlatform() {
      return RemotePlatform.CURRENT;
    }

    @Override
    public Process createProcess(NewCommandLine commandLine, ProgressIndicator indicator) throws ExecutionException {
      return createGeneralCommandLine(commandLine).createProcess();
    }

    @NotNull
    public GeneralCommandLine createGeneralCommandLine(NewCommandLine commandLine) {
      GeneralCommandLine generalCommandLine = new GeneralCommandLine(commandLine.prepareCommandLine(this));
      if (myRequest instanceof LocalRunner.LocalEnvironmentRequest) {
        generalCommandLine.withParentEnvironmentType(((LocalRunner.LocalEnvironmentRequest)myRequest).myParentEnvironmentType);
      }
      String inputFilePath = commandLine.getInputFilePath();
      if (inputFilePath != null) {
        generalCommandLine.withInput(new File(inputFilePath));
      }
      generalCommandLine.withCharset(commandLine.getCharset());
      String workingDirectory = commandLine.getWorkingDirectory();
      if (workingDirectory != null) {
        generalCommandLine.withWorkDirectory(workingDirectory);
      }
      generalCommandLine.withEnvironment(commandLine.getEnvironmentVariables());
      return generalCommandLine;
    }
  }
}
