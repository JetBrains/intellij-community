/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/**
 * @author cdr
 */
package com.intellij.ide.startup;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
public class StartupActionScriptManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.StartupActionScriptManager");

  @NonNls private static final String ACTION_SCRIPT_FILE = "action.script";

  private StartupActionScriptManager() {
  }

  public static synchronized void executeActionScript() throws IOException {
    List<ActionCommand> commands = loadActionScript();

    for (ActionCommand actionCommand : commands) {
      actionCommand.execute();
    }
    if (commands.size() > 0) {
      commands.clear();

      saveActionScript(commands);
    }
  }

  public static synchronized void addActionCommand(ActionCommand command) throws IOException {
    final List<ActionCommand> commands = loadActionScript();
    commands.add(command);
    saveActionScript(commands);
  }

  private static String getActionScriptPath() {
    String systemPath = PathManager.getPluginTempPath();
    return systemPath + File.separator + ACTION_SCRIPT_FILE;
  }

  private static List<ActionCommand> loadActionScript() throws IOException {
    File file = new File(getActionScriptPath());
    if (file.exists()) {
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
      try {
        //noinspection unchecked
        return (List<ActionCommand>)ois.readObject();
      }
      catch (ClassNotFoundException e) {
        // problem with scrambled code
        // fas fixed, but still appear because corrupted file still exists
        // return empty list.
        LOG.error("Internal file was corrupted. Problem is fixed.\nIf some plugins has been installed/uninstalled, please re-install/-uninstall them.", e);

        return new ArrayList<ActionCommand>();
      }
      finally {
        ois.close();
      }
    }
    else {
      return new ArrayList<ActionCommand>();
    }
  }

  private static void saveActionScript(List<ActionCommand> commands) throws IOException {
    File temp = new File(PathManager.getPluginTempPath());
    boolean exists = true;
    if (!temp.exists()) {
      exists = temp.mkdirs();
    }

    if (exists) {
      File file = new File(getActionScriptPath());
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file, false));
      try {
        oos.writeObject(commands);
      }
      finally {
        oos.close();
      }
    }
  }

  private static boolean canCreateFile(File file) {
    return FileUtil.ensureCanCreateFile(file);
  }

  public interface ActionCommand {
    void execute() throws IOException;
  }

  public static class CopyCommand implements Serializable, ActionCommand {
    @NonNls private static final String action = "copy";
    private final File mySource;
    private final File myDestination;

    public CopyCommand(File source, File destination) {
      myDestination = destination;
      mySource = source;
    }

    public String toString() {
      return action + "[" + mySource.getAbsolutePath() +
             (myDestination == null ? "" : ", " + myDestination.getAbsolutePath()) + "]";
    }

    public void execute() throws IOException {
      // create dirs for destination
      File parentFile = myDestination.getParentFile();
      if (! parentFile.exists())
        if (! myDestination.getParentFile().mkdirs()) {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        MessageFormat.format("<html>Cannot create parent directory [{0}] of {1}<br>Please, check your access rights on folder <br>{2}",
                                        parentFile.getAbsolutePath(), myDestination.getAbsolutePath(), parentFile.getParent()),
                                        "Installing Plugin",
                                        JOptionPane.ERROR_MESSAGE);
        }

      if (!mySource.exists()) {
        // NOTE: Please don't use LOG.error here - this will block IDEA startup in case of problems with plugin installation (IDEA-54045)

        //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
        System.err.println("Source file " + mySource.getAbsolutePath() + " does not exist for action " + this);
      }
      else if (!canCreateFile(myDestination)) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      MessageFormat.format("<html>Cannot copy {0}<br>to<br>{1}<br>Please, check your access rights on folder <br>{2}",
                                                           mySource.getAbsolutePath(),  myDestination.getAbsolutePath(), myDestination.getParent()),
                                      "Installing Plugin", JOptionPane.ERROR_MESSAGE);
      }
      else {
        FileUtil.copy(mySource, myDestination);
      }
    }

  }

  public static class UnzipCommand implements Serializable, ActionCommand {
    @NonNls private static final String action = "unzip";
    private File mySource;
    private FilenameFilter myFilenameFilter;
    private File myDestination;

    public UnzipCommand(File source, File destination) {
      this(source, destination, null);
    }

    public UnzipCommand(File source, File destination, FilenameFilter filenameFilter) {
      myDestination = destination;
      mySource = source;
      myFilenameFilter = filenameFilter;
    }

    public String toString() {
      return action + "[" + mySource.getAbsolutePath() +
             (myDestination == null ? "" : ", " + myDestination.getAbsolutePath()) + "]";
    }

    public void execute() throws IOException {
      if (!mySource.exists()) {
        //noinspection HardCodedStringLiteral
        System.err.println("Source file " + mySource.getAbsolutePath() + " does not exist for action " + this);
      }
      else if (!canCreateFile(myDestination)) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      MessageFormat.format("<html>Cannot unzip {0}<br>to<br>{1}<br>Please, check your access rights on folder <br>{2}",
                                                           mySource.getAbsolutePath(), myDestination.getAbsolutePath(), myDestination),
                                      "Installing Plugin", JOptionPane.ERROR_MESSAGE);
      }
      else {
        try {
          ZipUtil.extract(mySource, myDestination, myFilenameFilter);
        }
        catch(Exception ex) {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        MessageFormat.format("<html>Failed to extract ZIP file {0}<br>to<br>{1}<br>You may need to re-download the plugin you tried to install.",
                                                             mySource.getAbsolutePath(), myDestination.getAbsolutePath()),
                                        "Installing Plugin", JOptionPane.ERROR_MESSAGE);
        }
      }
    }

  }

  public static class DeleteCommand implements Serializable, ActionCommand {
    @NonNls private static final String action = "delete";
    private final File mySource;

    public DeleteCommand(File source) {
      mySource = source;
    }

    public String toString() {
      return action + "[" + mySource.getAbsolutePath() + "]";
    }

    public void execute() throws IOException {
      if (mySource != null && mySource.exists() && !FileUtil.delete(mySource)) {
        //noinspection HardCodedStringLiteral
        System.err.println("Action " + this + " failed.");
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      MessageFormat.format("<html>Cannot delete {0}<br>Please, check your access rights on folder <br>{1}",
                                                           mySource.getAbsolutePath(), mySource.getAbsolutePath()),
                                      "Installing Plugin", JOptionPane.ERROR_MESSAGE);
      }
    }
  }
}
