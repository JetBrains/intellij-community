/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.startup;

import com.intellij.openapi.application.PathManager;
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

  private static final String ACTION_SCRIPT_FILE = "action.script";

  private StartupActionScriptManager() { }

  public static synchronized void executeActionScript() throws IOException {
    try {
      List<ActionCommand> commands = loadActionScript();
      for (ActionCommand actionCommand : commands) {
        actionCommand.execute();
      }
    }
    finally {
      saveActionScript(null);  // deleting a file should not cause an exception
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
      List<ActionCommand> script = loadActionScript();
      script.addAll(commands);
      saveActionScript(script);
    }
  }

  private static File getActionScriptFile() {
    return new File(PathManager.getPluginTempPath(), ACTION_SCRIPT_FILE);
  }

  private static List<ActionCommand> loadActionScript() throws IOException {
    File scriptFile = getActionScriptFile();
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
        throw new IOException("Stream error: " + scriptFile, e);
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
      if (!(destDir.exists() || destDir.mkdirs())) {
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

      File destDir = destination.getParentFile();
      if (!(destDir.exists() || destDir.mkdirs())) {
        throw new IOException("Cannot create directory: " + destDir);
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