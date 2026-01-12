// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class StartupActionScriptManager {
  @ApiStatus.Internal
  public static final String ACTION_SCRIPT_FILE = "action.script";

  private StartupActionScriptManager() { }

  @ApiStatus.Internal
  public static synchronized void executeActionScript() throws IOException {
    var scriptFile = getActionScriptFile();
    try {
      var commands = loadActionScript(scriptFile);
      for (var command : commands) {
        command.execute();
      }
    }
    finally {
      // deleting a file should not cause an exception
      Files.deleteIfExists(scriptFile);
    }
  }

  @ApiStatus.Internal
  public static void executeActionScriptCommands(
    @NotNull List<? extends ActionCommand> commands,
    @NotNull Path oldTarget,
    @NotNull Path newTarget
  ) throws IOException {
    var fs = oldTarget.getFileSystem();
    for (var command : commands) {
      var toExecute = mapPaths(command, oldTarget, newTarget);
      if (toExecute != null) {
        toExecute.execute(fs);
      }
    }
  }

  public static synchronized void addActionCommand(@NotNull ActionCommand command) throws IOException {
    addActionCommands(List.of(command));
  }

  public static synchronized void addActionCommands(@NotNull List<? extends ActionCommand> commands) throws IOException {
    addActionCommands(commands, true);
  }

  @ApiStatus.Experimental
  public static synchronized void addActionCommandsToBeginning(@NotNull List<? extends ActionCommand> commands) throws IOException {
    addActionCommands(commands, false);
  }

  private static synchronized void addActionCommands(@NotNull List<? extends ActionCommand> commands, boolean toEndOfScript) throws IOException {
    List<ActionCommand> script = new ArrayList<>(), originalScript = null;
    var scriptFile = getActionScriptFile();
    if (Files.exists(scriptFile)) {
      originalScript = loadActionScript(scriptFile);
      script.addAll(originalScript);
    }
    if (toEndOfScript) {
      script.addAll(commands);
    }
    else {
      script.addAll(0, commands);
    }

    try {
      saveActionScript(script, scriptFile);
    }
    catch (Throwable t) {
      if (originalScript != null) {
        try {
          saveActionScript(originalScript, scriptFile);
        }
        catch (Throwable tt) {
          t.addSuppressed(tt);
        }
      }
      throw t;
    }
  }

  private static Path getActionScriptFile() {
    return PathManager.getStartupScriptDir().resolve(ACTION_SCRIPT_FILE);
  }

  @ApiStatus.Internal
  public static @NotNull List<ActionCommand> loadActionScript(@NotNull Path scriptFile) throws IOException {
    try (var ois = new ObjectInputStream(Files.newInputStream(scriptFile))) {
      var data = ois.readObject();
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
    try (var oos = new ObjectOutputStream(Files.newOutputStream(scriptFile))) {
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
    if (command instanceof CopyCommand copyCommand) {
      var destination = mapPath(copyCommand.myDestination, oldTarget, newTarget);
      if (destination != null) {
        return new CopyCommand(oldTarget.getFileSystem().getPath(copyCommand.mySource), destination);
      }
    }
    else if (command instanceof UnzipCommand unzipCommand) {
      var destination = mapPath(unzipCommand.myDestination, oldTarget, newTarget);
      if (destination != null) {
        return new UnzipCommand(oldTarget.getFileSystem().getPath(unzipCommand.mySource), destination, unzipCommand.myFilenameFilter);
      }
    }
    else if (command instanceof DeleteCommand deleteCommand) {
      var source = mapPath(deleteCommand.mySource, oldTarget, newTarget);
      if (source != null) {
        return new DeleteCommand(source);
      }
    }

    return null;
  }

  private static @Nullable Path mapPath(String path, Path oldTarget, Path newTarget) {
    var fsPath = oldTarget.getFileSystem().getPath(path);
    return fsPath.startsWith(oldTarget) ? newTarget.resolve(oldTarget.relativize(fsPath)) : null;
  }

  @ApiStatus.Internal
  public static synchronized void executeMarketplaceCommandsFromActionScript() throws IOException {
    var scriptFile = getActionScriptFile();
    @Nullable List<ActionCommand> remainingCommands = null;
    boolean marketplaceCommandsFound = false;
    try {
      var commands = loadActionScript(scriptFile);

      var partitioned = commands.stream().collect(Collectors.partitioningBy(command -> {
        if (command instanceof UnzipCommand unzipCommand) {
          return Path.of(unzipCommand.mySource).getFileName().toString().startsWith("marketplace");
        }
        else if (command instanceof DeleteCommand deleteCommand) {
          return Path.of(deleteCommand.mySource).getFileName().toString().equals("marketplace");
        }
        return false;
      }));

      var marketplaceCommands = partitioned.get(true);
      remainingCommands = partitioned.get(false);

      for (var command : marketplaceCommands) {
        marketplaceCommandsFound = true;
        command.execute();
      }
    } finally {
      if (remainingCommands == null || remainingCommands.isEmpty()) {
        Files.deleteIfExists(scriptFile);
      }
      else if (marketplaceCommandsFound) { // the file won't change if no marketplace commands were found
        saveActionScript(remainingCommands, scriptFile);
      }
    }
  }

  public interface ActionCommand {
    /** @deprecated implement {@link #execute(FileSystem)} */
    @Deprecated(forRemoval = true)
    default void execute() throws IOException {
      execute(FileSystems.getDefault());
    }

    void execute(@NotNull FileSystem fs) throws IOException;
  }

  public static final class CopyCommand implements Serializable, ActionCommand {
    @Serial private static final long serialVersionUID = 201708031943L;

    private final String mySource;
    private final String myDestination;

    public CopyCommand(@NotNull Path source, @NotNull Path destination) {
      mySource = source.toAbsolutePath().toString();
      myDestination = destination.toAbsolutePath().toString();
    }

    /** @deprecated Use {@link #CopyCommand(Path, Path)} */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("IO_FILE_USAGE")
    public CopyCommand(@NotNull File source, @NotNull File destination) {
      mySource = source.getAbsolutePath();
      myDestination = destination.getAbsolutePath();
    }

    @Override
    public void execute(@NotNull FileSystem fs) throws IOException {
      Path source = fs.getPath(mySource), destination = fs.getPath(myDestination);
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
    @Serial private static final long serialVersionUID = 201708031943L;

    private final String mySource;
    private final String myDestination;
    private final @Nullable Predicate<? super String> myFilenameFilter;

    public UnzipCommand(@NotNull Path source, @NotNull Path destination) {
      this(source, destination, null);
    }

    /** @deprecated Use {@link #UnzipCommand(Path, Path)} */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("IO_FILE_USAGE")
    public UnzipCommand(@NotNull File source, @NotNull File destination) {
      this(source.toPath(), destination.toPath());
    }

    public UnzipCommand(@NotNull Path source, @NotNull Path destination, @Nullable Predicate<? super String> filenameFilter) {
      mySource = source.toAbsolutePath().toString();
      myDestination = destination.toAbsolutePath().toString();
      myFilenameFilter = filenameFilter;
    }

    @Override
    public void execute(@NotNull FileSystem fs) throws IOException {
      Path source = fs.getPath(mySource), destination = fs.getPath(myDestination);
      if (!Files.isRegularFile(source)) {
        throw new IOException("Source file missing: " + mySource);
      }
      Files.createDirectories(destination);

      new Decompressor.Zip(source)
        .withZipExtensions()
        .filter(myFilenameFilter)
        .extract(destination);
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
    @Serial private static final long serialVersionUID = 201708031943L;

    private final String mySource;

    public DeleteCommand(@NotNull Path source) {
      mySource = source.toAbsolutePath().toString();
    }

    /** @deprecated Use {@link #DeleteCommand(Path)} */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("IO_FILE_USAGE")
    public DeleteCommand(@NotNull File source) {
      mySource = source.getAbsolutePath();
    }

    @Override
    public void execute(@NotNull FileSystem fs) throws IOException {
      NioFiles.deleteRecursively(fs.getPath(mySource));
    }

    @Override
    public String toString() {
      return "delete[" + mySource + ']';
    }
  }
}
