// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class StartupActionScriptManager {
  public static final String STARTUP_WIZARD_MODE = "StartupWizardMode";
  public static final String ACTION_SCRIPT_FILE = "action.script";

  private StartupActionScriptManager() { }

  public static synchronized void executeActionScript() throws IOException {
    Path scriptFile = getActionScriptFile();
    List<ActionCommand> commands = null;
    try {
      commands = loadActionScript(scriptFile);
      for (ActionCommand command : commands) {
        command.execute();
      }
    }
    finally {
      // deleting a file should not cause an exception
      if (commands == null /* error occurred on load */ || !commands.isEmpty() /* not empty list means that there is some data */) {
        Files.deleteIfExists(scriptFile);
      }
    }
  }

  public static synchronized void executeActionScript(@NotNull Path scriptFile, @NotNull Path oldTarget, @NotNull Path newTarget) throws IOException {
    executeActionScriptCommands(loadActionScript(scriptFile), oldTarget, newTarget);
  }

  public static void executeActionScriptCommands(List<? extends ActionCommand> commands,
                                                 @NotNull Path oldTarget,
                                                 @NotNull Path newTarget) throws IOException {
    for (ActionCommand command : commands) {
      ActionCommand toExecute = mapPaths(command, oldTarget, newTarget);
      if (toExecute != null) {
        toExecute.execute();
      }
    }
  }

  public static synchronized void addActionCommand(ActionCommand command) throws IOException {
    addActionCommands(Collections.singletonList(command));
  }

  public static synchronized void addActionCommands(@NotNull List<? extends ActionCommand> commands) throws IOException {
    if (Boolean.getBoolean(STARTUP_WIZARD_MODE)) {
      for (ActionCommand command : commands) {
        command.execute();
      }
      return;
    }

    List<ActionCommand> script;
    Path actionScriptFile = getActionScriptFile();
    try {
      List<ActionCommand> savedScript = loadActionScript(actionScriptFile);
      script = new ArrayList<>(savedScript.size() + commands.size());
      script.addAll(savedScript);
      script.addAll(commands);
    }
    catch (ObjectStreamException e) {
      Logger.getInstance(StartupActionScriptManager.class).warn(e);
      script = new ArrayList<>(commands);
    }

    saveActionScript(script, actionScriptFile);
  }

  private static @NotNull Path getActionScriptFile() {
    return Path.of(PathManager.getPluginTempPath(), ACTION_SCRIPT_FILE);
  }

  public static @NotNull List<ActionCommand> loadActionScript(@NotNull Path scriptFile) throws IOException {
    try (ObjectInput ois = new ObjectInputStream(Files.newInputStream(scriptFile))) {
      Object data = ois.readObject();
      if (data instanceof ActionCommand[]) {
        return Arrays.asList((ActionCommand[])data);
      }
      else if (data instanceof List && ((List<?>)data).isEmpty()) {
        return Collections.emptyList();
      }
      else {
        throw new IOException("An unexpected object: " + data + "/" + data.getClass());
      }
    }
    catch (NoSuchFileException | AccessDeniedException e) {
      return Collections.emptyList();
    }
    catch (ReflectiveOperationException e) {
      throw (StreamCorruptedException)new StreamCorruptedException("Stream error: " + scriptFile).initCause(e);
    }
  }

  public static void saveActionScript(@Nullable List<ActionCommand> commands, @NotNull Path scriptFile)
    throws IOException {
    if (commands == null) {
      Files.deleteIfExists(scriptFile);
    }
    else {
      Files.createDirectories(scriptFile.getParent());
      try (ObjectOutput oos = new ObjectOutputStream(Files.newOutputStream(scriptFile))) {
        oos.writeObject(commands.toArray(ActionCommand.EMPTY_ARRAY));
      }
    }
  }

  private static ActionCommand mapPaths(ActionCommand command, Path oldTarget, Path newTarget) {
    if (command instanceof CopyCommand) {
      Path destination = mapPath(((CopyCommand)command).destination, oldTarget, newTarget);
      if (destination != null) {
        return new CopyCommand(Paths.get(((CopyCommand)command).source), destination);
      }
    }
    else if (command instanceof UnzipCommand) {
      UnzipCommand unzipCommand = (UnzipCommand)command;
      Path destination = mapPath(unzipCommand.myDestination, oldTarget, newTarget);
      if (destination != null) {
        return new UnzipCommand(Paths.get(unzipCommand.mySource), destination, unzipCommand.myFilenameFilter);
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
    ActionCommand[] EMPTY_ARRAY = new ActionCommand[0];
    void execute() throws IOException;
  }

  public static final class CopyCommand implements Serializable, ActionCommand {
    private static final long serialVersionUID = 201708031943L;

    private final String source;
    private final String destination;

    public CopyCommand(@NotNull Path source, @NotNull Path destination) {
      this.source = source.toAbsolutePath().toString();
      this.destination = destination.toAbsolutePath().toString();
    }

    /**
     * @deprecated Use {@link #CopyCommand(Path, Path)}
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    public CopyCommand(@NotNull File source, @NotNull File destination) {
      this.source = source.getAbsolutePath();
      this.destination = destination.getAbsolutePath();
    }

    @Override
    public void execute() throws IOException {
      Path destination = Paths.get(this.destination);
      Path destDir = destination.getParent();
      Files.createDirectories(destDir);
      Files.copy(Paths.get(source), destination);
    }

    @Override
    @NonNls
    public String toString() {
      return "copy[" + source + "," + destination + "]";
    }

    public String getSource() {
      return source;
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

    /**
     * @deprecated Use {@link #UnzipCommand(Path, Path)}
     */
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
      Path source = Paths.get(mySource);
      Path destination = Paths.get(myDestination);

      if (!Files.isRegularFile(source)) {
        throw new IOException("Source file missing: " + source);
      }

      Files.createDirectories(destination);
      new Decompressor.Zip(source).filter(myFilenameFilter).extract(destination);
    }

    @Override
    @NonNls
    public String toString() {
      return "unzip[" + mySource + "," + myDestination + "]";
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

    /**
     * @deprecated Use {@link #DeleteCommand(Path)}
     */
    @Deprecated
    public DeleteCommand(@NotNull File source) {
      mySource = source.getAbsolutePath();
    }

    @Override
    public void execute() throws IOException {
      // source here it is directory - Files.deleteIfExists must be not used here
      // todo use NioFiles (see IDEA-CR-69550)
      FileUtilRt.delete(new File(mySource));
    }

    @Override
    @NonNls
    public String toString() {
      return "delete[" + mySource + "]";
    }
  }
}