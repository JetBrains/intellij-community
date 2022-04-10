// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public final class StartupActionScriptManager {
  public static final String STARTUP_WIZARD_MODE = "StartupWizardMode";
  public static final String ACTION_SCRIPT_FILE = "action.script";

  private StartupActionScriptManager() { }

  @ApiStatus.Internal
  public static synchronized void executeActionScript() throws IOException {
    Path scriptFile = getActionScriptFile();
    try {
      List<ActionCommand> commands = loadActionScript(scriptFile);
      for (ActionCommand command : commands) {
        command.execute();
      }
    }
    finally {
      // deleting a file should not cause an exception
      Files.deleteIfExists(scriptFile);
    }
  }

  @ApiStatus.Internal
  public static void executeActionScriptCommands(@NotNull List<? extends ActionCommand> commands,
                                                 @NotNull Path oldTarget,
                                                 @NotNull Path newTarget) throws IOException {
    for (ActionCommand command : commands) {
      ActionCommand toExecute = mapPaths(command, oldTarget, newTarget);
      if (toExecute != null) {
        toExecute.execute();
      }
    }
  }

  public static synchronized void addActionCommand(@NotNull ActionCommand command) throws IOException {
    addActionCommands(List.of(command));
  }

  public static synchronized void addActionCommands(@NotNull List<? extends ActionCommand> commands) throws IOException {
    if (Boolean.getBoolean(STARTUP_WIZARD_MODE)) {
      for (ActionCommand command : commands) {
        command.execute();
      }
    }
    else {
      List<ActionCommand> script = new ArrayList<>(), originalScript = null;
      Path scriptFile = getActionScriptFile();
      if (Files.exists(scriptFile)) {
        originalScript = loadActionScript(scriptFile);
        script.addAll(originalScript);
      }
      script.addAll(commands);

      try {
        saveActionScript(script, scriptFile);
      }
      catch (Throwable t) {
        if (originalScript != null) {
          try {
            saveActionScript(originalScript, scriptFile);
          }
          catch (Throwable tt) { t.addSuppressed(tt); }
        }
        throw t;
      }
    }
  }

  private static Path getActionScriptFile() {
    return Path.of(PathManager.getPluginTempPath(), ACTION_SCRIPT_FILE);
  }

  @ApiStatus.Internal
  public static @NotNull List<ActionCommand> loadActionScript(@NotNull Path scriptFile) throws IOException {
    try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(scriptFile))) {
      Object data = ois.readObject();
      if (data instanceof ActionCommand[]) {
        return Arrays.asList((ActionCommand[])data);
      }
      else {
        throw new IOException("An unexpected object: " + data + "/" + data.getClass() + " in " + scriptFile);
      }
    }
    catch (NoSuchFileException | AccessDeniedException e) {
      return List.of();
    }
    catch (ReflectiveOperationException e) {
      throw (StreamCorruptedException)new StreamCorruptedException("Stream error: " + scriptFile).initCause(e);
    }
  }

  @ApiStatus.Internal
  public static void saveActionScript(@NotNull List<ActionCommand> commands, @NotNull Path scriptFile) throws IOException {
    Files.createDirectories(scriptFile.getParent());
    try (ObjectOutput oos = new ObjectOutputStream(Files.newOutputStream(scriptFile))) {
      oos.writeObject(commands.toArray(new ActionCommand[0]));
    }
    catch (Throwable t) {
      try {
        Files.deleteIfExists(scriptFile);
      }
      catch (IOException e) { t.addSuppressed(e); }
      throw t;
    }
  }

  private static @Nullable ActionCommand mapPaths(ActionCommand command, Path oldTarget, Path newTarget) {
    if (command instanceof CopyCommand) {
      Path destination = mapPath(((CopyCommand)command).myDestination, oldTarget, newTarget);
      if (destination != null) {
        return new CopyCommand(Paths.get(((CopyCommand)command).mySource), destination);
      }
    }
    else if (command instanceof UnzipCommand) {
      UnzipCommand unzipCommand = (UnzipCommand)command;
      Path destination = mapPath(unzipCommand.myDestination, oldTarget, newTarget);
      if (destination != null) {
        return new UnzipCommand(Path.of(unzipCommand.mySource), destination, unzipCommand.myFilenameFilter);
      }
    }
    else if (command instanceof DeleteCommand) {
      Path source = mapPath(((DeleteCommand)command).mySource, oldTarget, newTarget);
      if (source != null) {
        return new DeleteCommand(source);
      }
    }

    return null;
  }

  private static @Nullable Path mapPath(String path, Path oldTarget, Path newTarget) {
    String oldTargetPath = oldTarget.toString();
    if (path.startsWith(oldTargetPath)) {
      if (path.length() == oldTargetPath.length()) {
        return newTarget;
      }
      if (path.charAt(oldTargetPath.length()) == File.separatorChar) {
        return newTarget.resolve(path.substring(oldTargetPath.length() + 1));
      }
    }
    return null;
  }

  public interface ActionCommand {
    void execute() throws IOException;
  }

  public static final class CopyCommand implements Serializable, ActionCommand {
    private static final long serialVersionUID = 201708031943L;

    private final String mySource;
    private final String myDestination;

    public CopyCommand(@NotNull Path source, @NotNull Path destination) {
      mySource = source.toAbsolutePath().toString();
      myDestination = destination.toAbsolutePath().toString();
    }

    /** @deprecated Use {@link #CopyCommand(Path, Path)} */
    @Deprecated(forRemoval = true)
    public CopyCommand(@NotNull File source, @NotNull File destination) {
      mySource = source.getAbsolutePath();
      myDestination = destination.getAbsolutePath();
    }

    @Override
    public void execute() throws IOException {
      Path source = Path.of(mySource), destination = Path.of(myDestination);
      if (!Files.isRegularFile(source)) {
        throw new IOException("Source file missing: " + mySource);
      }
      Files.createDirectories(destination.getParent());
      Files.copy(source, destination);
    }

    @Override
    public String toString() {
      return "copy[" + mySource + ',' + myDestination + ']';
    }

    public String getSource() {
      return mySource;
    }
  }

  public static final class UnzipCommand implements Serializable, ActionCommand {
    private static final long serialVersionUID = 201708031943L;

    private final String mySource;
    private final String myDestination;
    private final @Nullable Predicate<? super String> myFilenameFilter;

    public UnzipCommand(@NotNull Path source, @NotNull Path destination) {
      this(source, destination, null);
    }

    /** @deprecated Use {@link #UnzipCommand(Path, Path)} */
    @Deprecated
    public UnzipCommand(@NotNull File source, @NotNull File destination) {
      this(source.toPath(), destination.toPath());
    }

    public UnzipCommand(@NotNull Path source, @NotNull Path destination, @Nullable Predicate<? super String> filenameFilter) {
      mySource = source.toAbsolutePath().toString();
      myDestination = destination.toAbsolutePath().toString();
      myFilenameFilter = filenameFilter;
    }

    @Override
    public void execute() throws IOException {
      Path source = Path.of(mySource), destination = Path.of(myDestination);
      if (!Files.isRegularFile(source)) {
        throw new IOException("Source file missing: " + mySource);
      }
      Files.createDirectories(destination);
      new Decompressor.Zip(source).filter(myFilenameFilter).extract(destination);
    }

    @Override
    public String toString() {
      return "unzip[" + mySource + ',' + myDestination + ',' + myFilenameFilter + ']';
    }

    public String getSource() {
      return mySource;
    }
  }

  public static final class DeleteCommand implements Serializable, ActionCommand {
    private static final long serialVersionUID = 201708031943L;

    private final String mySource;

    public DeleteCommand(@NotNull Path source) {
      mySource = source.toAbsolutePath().toString();
    }

    /** @deprecated Use {@link #DeleteCommand(Path)} */
    @Deprecated
    public DeleteCommand(@NotNull File source) {
      mySource = source.getAbsolutePath();
    }

    @Override
    public void execute() throws IOException {
      NioFiles.deleteRecursively(Path.of(mySource));
    }

    @Override
    public String toString() {
      return "delete[" + mySource + ']';
    }
  }
}
