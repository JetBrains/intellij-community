/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileChooserAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: Vassiliy.Kudryashov
 */
public class JdkPopupAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.actions.JDKPopupAction");

  public JdkPopupAction() {
    super("Show Quick list", "", AllIcons.General.AddJdk);
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = isEnabledInCurrentOS();
    if (enabled) {
      FileSystemTree tree = FileSystemTree.DATA_KEY.getData(e.getDataContext());
      if (tree == null || Boolean.TRUE != tree.getData(JavaSdkImpl.KEY)) {
        enabled = false;
      }
    }
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final JComponent component;
    final boolean showInMiddle;
    InputEvent inputEvent = e.getInputEvent();
    Object source = inputEvent != null ? inputEvent.getSource() : null;
    if (source instanceof JComponent) {
      component = (JComponent)source;
      showInMiddle = false;
    }
    else {
      Component c = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
      component = c instanceof JComponent? (JComponent)c : null;
      showInMiddle = true;
    }

    if (!isEnabledInCurrentOS() || component == null) return;

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      final ArrayList<Pair<File, String>> jdkLocations = retrieveJDKLocations();

      if (jdkLocations.isEmpty()) {
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> showPopupMenu(e, jdkLocations, showInMiddle, component));
    }, "Looking for JDK locations...", false, e.getProject(), component);
  }

  private static boolean isEnabledInCurrentOS() {
    return SystemInfo.isWindows;
  }

  private static void showPopupMenu(AnActionEvent e,
                             final ArrayList<Pair<File, String>> jdkLocations,
                             boolean showInMiddle,
                             JComponent component) {
    ActionPopupMenu menu =
      ActionManager.getInstance().createActionPopupMenu(e.getPlace(), new ActionGroup() {
        @NotNull
        @Override
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          List<AnAction> result = new ArrayList<>();
          for (final Pair<File, String> homes : jdkLocations) {
            result.add(new FileChooserAction("", null, null) {
              @Override
              protected void update(FileSystemTree fileChooser, AnActionEvent e) {
                e.getPresentation().setText(homes.getSecond(), false);
                boolean selected = false;
                VirtualFile selectedFile = fileChooser.getSelectedFile();
                if (selectedFile != null) {
                  selected = homes.getFirst().getAbsolutePath().equals(VfsUtilCore.virtualToIoFile(selectedFile).getAbsolutePath());
                }
                e.getPresentation().setIcon(selected ? AllIcons.Diff.CurrentLine : null);
              }

              @Override
              protected void actionPerformed(FileSystemTree fileChooser, AnActionEvent e) {
                fileChooser.select(VfsUtil.findFileByIoFile(homes.getFirst(), true), null);
              }
            });
          }
          return result.toArray(new AnAction[result.size()]);
        }
      });
    JPopupMenu menuComponent = menu.getComponent();
    if (showInMiddle) {
      menuComponent
        .show(component, (component.getWidth() - menuComponent.getWidth()) / 2,
              (component.getHeight() - menuComponent.getHeight()) / 2);
    }
    else {
      menuComponent.show(component, 0, component.getHeight());
    }
  }

  private static ArrayList<Pair<File, String>> retrieveJDKLocations() {
    ArrayList<Pair<File, String>> jdkLocations = new ArrayList<>();
    Collection<String> homePaths = JavaSdk.getInstance().suggestHomePaths();
    for (final String path : homePaths) {
      try {
        File file = new File(path);
        File javaExe = new File(new File(file, "bin"), "java.exe");
        ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(javaExe.getAbsolutePath(), "-version"));
        List<String> lines = output.getStderrLines();
        if (lines.isEmpty()) {
          lines = output.getStdoutLines();
        }
        StringBuilder stringBuilder = new StringBuilder();
        if (lines.size() == 3) {
          stringBuilder.append("JDK ");
          String line = lines.get(1);
          int pos = line.indexOf("(build ");
          if (pos != -1) {
            stringBuilder.append(line.substring(pos + 7, line.length() - 1));
          }
          line = lines.get(2);
          pos = line.indexOf(" (build");
          if (pos != -1) {
            String substring = line.substring(0, pos);
            stringBuilder.append(" (").append(substring).append(")");
          }
        }
        else {
          stringBuilder.append(file.getName());
        }
        jdkLocations.add(Pair.create(file, stringBuilder.toString()));
      }
      catch (ExecutionException e) {
        LOG.debug(e);
      }
    }
    return jdkLocations;
  }
}
