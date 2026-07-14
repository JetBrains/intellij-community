// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("UseOptimizedEelFunctions")
public final class StartupActionScriptManager {
  @ApiStatus.Internal
  public static final String ACTION_SCRIPT_FILE = "action.script";

  private StartupActionScriptManager() { }

  @ApiStatus.Internal
  public static synchronized void executeActionScript() throws IOException {
    var scriptFile = getActionScriptFile();
    try {
      var commands = loadActionScript(scriptFile);
      var fs = FileSystems.getDefault();
      for (var command : commands) {
        command.execute(fs);
      }
    }
    finally {
      Files.deleteIfExists(scriptFile);  // deleting a file should not cause an exception
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
      var toExecute = mapPaths(command, fs, oldTarget, newTarget);
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

  private static synchronized void addActionCommands(List<? extends ActionCommand> commands, boolean toEndOfScript) throws IOException {
    var script = new ArrayList<ActionCommand>();
    var originalScript = (List<ActionCommand>)null;
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
    try {
      var bytes = Files.readAllBytes(scriptFile);
      if (bytes.length > 2 && bytes[0] == (byte)0xAC && bytes[1] == (byte)0xED) {  // `ObjectStreamConstants.STREAM_MAGIC`
        try (var ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
          var data = ois.readObject();
          if (data instanceof ActionCommand[]) {
            return Arrays.asList((ActionCommand[])data);
          }
          else {
            throw new IOException("An unexpected object: " + data + "/" + data.getClass() + " in " + scriptFile);
          }
        }
      }
      else {
        @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"}) var separator = java.io.File.pathSeparator;
        var commands = new ArrayList<ActionCommand>();
        try (var in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
          String line;
          while ((line = in.readLine()) != null) {
            var parts = line.split(separator);
            if ("copy".equals(parts[0]) && parts.length == 3) {
              commands.add(new CopyCommand(parts[1], parts[2]));
            }
            else if ("delete".equals(parts[0]) && parts.length == 2) {
              commands.add(new DeleteCommand(parts[1]));
            }
            else if ("unzip".equals(parts[0]) && parts.length == 3) {
              commands.add(new UnzipCommand(parts[1], parts[2]));
            }
            else {
              throw new IllegalArgumentException("bad command: " + line);
            }
          }
        }
        return commands;
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
    try (var out = Files.newBufferedWriter(scriptFile, StandardCharsets.UTF_8)) {
      for (var command : commands) {
        switch (command) {
          case CopyCommand copyCommand -> {
            writeStrings(out, "copy", copyCommand.mySource, copyCommand.myDestination);
          }
          case DeleteCommand deleteCommand -> {
            writeStrings(out, "delete", deleteCommand.mySource);
          }
          case UnzipCommand unzipCommand -> {
            writeStrings(out, "unzip", unzipCommand.mySource, unzipCommand.myDestination);
          }
        }
      }
    }
    catch (Throwable t) {
      try {
        Files.deleteIfExists(scriptFile);
      }
      catch (IOException e) {
        t.addSuppressed(e);
      }
      throw t;
    }
  }

  private static void writeStrings(BufferedWriter out, String... data) throws IOException {
    @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"}) var separator = java.io.File.pathSeparatorChar;
    for (int i = 0; i < data.length; i++) {
      out.write(data[i]);
      if (i < data.length - 1) {
        out.write(separator);
      }
      else {
        out.newLine();
      }
    }
  }

  private static @Nullable ActionCommand mapPaths(ActionCommand command, FileSystem fs, Path oldTarget, Path newTarget) {
    switch (command) {
      case CopyCommand copyCommand -> {
        var destination = mapPath(fs.getPath(copyCommand.myDestination), oldTarget, newTarget);
        if (destination != null) {
          return new CopyCommand(fs.getPath(copyCommand.mySource), destination);
        }
      }
      case UnzipCommand unzipCommand -> {
        var destination = mapPath(fs.getPath(unzipCommand.myDestination), oldTarget, newTarget);
        if (destination != null) {
          return new UnzipCommand(fs.getPath(unzipCommand.mySource), destination, unzipCommand.myFilenameFilter);
        }
      }
      case DeleteCommand deleteCommand -> {
        var source = mapPath(fs.getPath(deleteCommand.mySource), oldTarget, newTarget);
        if (source != null) {
          return new DeleteCommand(source);
        }
      }
    }

    return null;
  }

  private static @Nullable Path mapPath(Path path, Path oldTarget, Path newTarget) {
    return path.startsWith(oldTarget) ? newTarget.resolve(oldTarget.relativize(path)) : null;
  }

  @ApiStatus.Internal
  public static synchronized void executeMarketplaceCommandsFromActionScript() throws IOException {
    var scriptFile = getActionScriptFile();
    var remainingCommands = (List<ActionCommand>)null;
    var marketplaceCommandsFound = false;
    try {
      var commands = loadActionScript(scriptFile);

      var partitioned = commands.stream().collect(Collectors.partitioningBy(command -> switch (command) {
        case UnzipCommand unzipCommand -> Path.of(unzipCommand.mySource).getFileName().toString().startsWith("marketplace");
        case DeleteCommand deleteCommand -> Path.of(deleteCommand.mySource).getFileName().toString().equals("marketplace");
        default -> false;
      }));

      var marketplaceCommands = partitioned.get(true);
      remainingCommands = partitioned.get(false);

      var fs = FileSystems.getDefault();
      for (var command : marketplaceCommands) {
        marketplaceCommandsFound = true;
        command.execute(fs);
      }
    }
    finally {
      if (remainingCommands == null || remainingCommands.isEmpty()) {
        Files.deleteIfExists(scriptFile);
      }
      else if (marketplaceCommandsFound) { // the file won't change if no marketplace commands were found
        saveActionScript(remainingCommands, scriptFile);
      }
    }
  }

  public sealed interface ActionCommand {
    void execute(@NotNull FileSystem fs) throws IOException;
  }

  public static final class CopyCommand implements Serializable, ActionCommand {
    @Serial private static final long serialVersionUID = 201708031943L;

    private final String mySource;
    private final String myDestination;

    public CopyCommand(@NotNull Path source, @NotNull Path destination) {
      this(source.toAbsolutePath().toString(), destination.toAbsolutePath().toString());
    }

    private CopyCommand(String source, String destination) {
      mySource = source;
      myDestination = destination;
    }

    /// @deprecated Use [#CopyCommand(Path, Path)]
    @Deprecated(forRemoval = true)
    @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
    public CopyCommand(@NotNull java.io.File source, @NotNull java.io.File destination) {
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
      this(source.toAbsolutePath().toString(), destination.toAbsolutePath().toString());
    }

    /// @deprecated Use [#UnzipCommand(Path, Path)]
    @Deprecated(forRemoval = true)
    @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
    public UnzipCommand(@NotNull java.io.File source, @NotNull java.io.File destination) {
      this(source.toPath(), destination.toPath());
    }

    /// @deprecated no longer supported; repack the archive in advance if needed
    @Deprecated(forRemoval = true)
    @SuppressWarnings("DeprecatedIsStillUsed")
    public UnzipCommand(@NotNull Path source, @NotNull Path destination, @Nullable Predicate<? super String> filenameFilter) {
      mySource = source.toAbsolutePath().toString();
      myDestination = destination.toAbsolutePath().toString();
      myFilenameFilter = filenameFilter;
    }

    private UnzipCommand(String source, String destination) {
      mySource = source;
      myDestination = destination;
      myFilenameFilter = null;
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
      this(source.toAbsolutePath().toString());
    }

    private DeleteCommand(String source) {
      mySource = source;
    }

    /// @deprecated Use [#DeleteCommand(Path)]
    @Deprecated(forRemoval = true)
    @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
    public DeleteCommand(@NotNull java.io.File source) {
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
