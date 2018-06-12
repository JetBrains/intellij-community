// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class StartupActionScriptManager {
  public static final String STARTUP_WIZARD_MODE = "StartupWizardMode";
  public static final String ACTION_SCRIPT_FILE = "action.script";

  private StartupActionScriptManager() { }

  public static synchronized void executeActionScript() throws IOException {
    try {
      List<ActionCommand> commands = loadActionScript(getActionScriptFile());
      for (ActionCommand command : commands) {
        command.execute();
      }
    }
    finally {
      saveActionScript(null);  // deleting a file should not cause an exception
    }
  }

  public static synchronized void executeActionScript(@NotNull File scriptFile, @NotNull File oldTarget, @NotNull File newTarget) throws IOException {
    List<ActionCommand> commands = loadActionScript(scriptFile);
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

  public static synchronized void addActionCommands(List<ActionCommand> commands) throws IOException {
    if (Boolean.getBoolean(STARTUP_WIZARD_MODE)) {
      for (ActionCommand command : commands) {
        command.execute();
      }
    }
    else {
      List<ActionCommand> script;
      try {
        script = loadActionScript(getActionScriptFile());
        script.addAll(commands);
      }
      catch (ObjectStreamException e) {
        Logger.getInstance(StartupActionScriptManager.class).warn(e);
        script = new ArrayList<>(commands);
      }

      saveActionScript(script);
    }
  }

  private static File getActionScriptFile() {
    return new File(PathManager.getPluginTempPath(), ACTION_SCRIPT_FILE);
  }

  private static List<ActionCommand> loadActionScript(File scriptFile) throws IOException {
    if (scriptFile.isFile()) {
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(scriptFile))) {
        Object data = ois.readObject();
        if (data instanceof ActionCommand[]) {
          return new ArrayList<>(Arrays.asList((ActionCommand[])data));
        }
        else if (data instanceof List && ((List)data).size() == 0) {
          return new ArrayList<>();
        }
        else {
          throw new IOException("Unexpected object: " + data + "/" + data.getClass());
        }
      }
      catch (ReflectiveOperationException e) {
        throw (StreamCorruptedException)new StreamCorruptedException("Stream error: " + scriptFile).initCause(e);
      }
    }

    return new ArrayList<>();
  }

  private static void saveActionScript(@Nullable List<ActionCommand> commands) throws IOException {
    File scriptFile = getActionScriptFile();
    if (commands != null) {
      File tempDir = scriptFile.getParentFile();
      if (!(tempDir.exists() || tempDir.mkdirs())) {
        throw new IOException("Cannot create directory: " + tempDir);
      }
      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(scriptFile, false))) {
        oos.writeObject(commands.toArray(ActionCommand.EMPTY_ARRAY));
      }
    }
    else if (scriptFile.exists()) {
      FileUtilRt.delete(scriptFile);
    }
  }

  private static ActionCommand mapPaths(ActionCommand command, File oldTarget, File newTarget) {
    if (command instanceof CopyCommand) {
      File destination = mapPath(((CopyCommand)command).myDestination, oldTarget, newTarget);
      if (destination != null) {
        return new CopyCommand(new File(((CopyCommand)command).mySource), destination);
      }
    }
    else if (command instanceof UnzipCommand) {
      File destination = mapPath(((UnzipCommand)command).myDestination, oldTarget, newTarget);
      if (destination != null) {
        return new UnzipCommand(new File(((UnzipCommand)command).mySource), destination, ((UnzipCommand)command).myFilenameFilter);
      }
    }
    else if (command instanceof DeleteCommand) {
      File source = mapPath(((DeleteCommand)command).mySource, oldTarget, newTarget);
      if (source != null) {
        return new DeleteCommand(source);
      }
    }

    return null;
  }

  private static File mapPath(String path, File oldTarget, File newTarget) {
    String oldTargetPath = oldTarget.getPath();
    if (path.startsWith(oldTargetPath)) {
      if (path.length() == oldTargetPath.length()) {
        return newTarget;
      }
      if (path.charAt(oldTargetPath.length()) == File.separatorChar) {
        return new File(newTarget, path.substring(oldTargetPath.length() + 1));
      }
    }
    return null;
  }


  public interface ActionCommand {
    ActionCommand[] EMPTY_ARRAY = new ActionCommand[0];
    void execute() throws IOException;
  }

  public static class CopyCommand implements Serializable, ActionCommand {
    private static final long serialVersionUID = 201708031943L;

    private final String mySource;
    private final String myDestination;

    public CopyCommand(@NotNull File source, @NotNull File destination) {
      mySource = source.getAbsolutePath();
      myDestination = destination.getAbsolutePath();
    }

    @Override
    public void execute() throws IOException {
      File source = new File(mySource), destination = new File(myDestination);

      if (!source.isFile()) {
        throw new IOException("Source file missing: " + source);
      }

      File destDir = destination.getParentFile();
      if (!(destDir.isDirectory() || destDir.mkdirs())) {
        throw new IOException("Cannot create directory: " + destDir);
      }

      FileUtilRt.copy(source, destination);
    }

    @Override
    public String toString() {
      return "copy[" + mySource + "," + myDestination + "]";
    }
  }

  public static class UnzipCommand implements Serializable, ActionCommand {
    private static final long serialVersionUID = 201708031943L;

    private final String mySource;
    private final String myDestination;
    private final FilenameFilter myFilenameFilter;

    public UnzipCommand(@NotNull File source, @NotNull File destination) {
      this(source, destination, null);
    }

    public UnzipCommand(@NotNull File source, @NotNull File destination, FilenameFilter filenameFilter) {
      mySource = source.getAbsolutePath();
      myDestination = destination.getAbsolutePath();
      myFilenameFilter = filenameFilter;
    }

    @Override
    public void execute() throws IOException {
      File source = new File(mySource), destination = new File(myDestination);

      if (!source.isFile()) {
        throw new IOException("Source file missing: " + source);
      }

      if (!(destination.isDirectory() || destination.mkdirs())) {
        throw new IOException("Cannot create directory: " + destination);
      }

      ZipUtil.extract(source, destination, myFilenameFilter);
    }

    @Override
    public String toString() {
      return "unzip[" + mySource + "," + myDestination + "]";
    }
  }

  public static class DeleteCommand implements Serializable, ActionCommand {
    private static final long serialVersionUID = 201708031943L;

    private final String mySource;

    public DeleteCommand(@NotNull File source) {
      mySource = source.getAbsolutePath();
    }

    @Override
    public void execute() throws IOException {
      File source = new File(mySource);
      if (source.exists() && !FileUtilRt.delete(source)) {
        throw new IOException("Cannot delete: " + source);
      }
    }

    @Override
    public String toString() {
      return "delete[" + mySource + "]";
    }
  }
}