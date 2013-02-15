/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileChooserAction;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.SwingWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * User: Vassiliy.Kudryashov
 */
public class JdkPopupAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.actions.JDKPopupAction");
  private ArrayList<Pair<File, String>> myJDKHomes = new ArrayList<Pair<File, String>>();

  public JdkPopupAction() {
    super("Show Quick list", "", AllIcons.General.AddJdk);
    if (/*SystemInfo.isWindows*/false) {
      new SwingWorker() {
        ArrayList<Pair<File, String>> myResult = new ArrayList<Pair<File, String>>();

        @Override
        public Object construct() {
          Collection<String> homePaths = JavaSdk.getInstance().suggestHomePaths();
          for (final String path : homePaths) {
            try {
              File file = new File(path);
              File javaExe = new File(new File(file, "bin"), "java.exe");
              ProcessOutput output = ExecUtil.execAndGetOutput(Arrays.asList(javaExe.getAbsolutePath(), "-version"), null);
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
              myResult.add(Pair.create(file, stringBuilder.toString()));
            }
            catch (ExecutionException e) {
              LOG.debug(e);
            }
          }
          return myResult;
        }

        @Override
        public void finished() {
          myJDKHomes.addAll(myResult);
        }
      }.start();
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ActionPopupMenu menu =
      ActionManager.getInstance().createActionPopupMenu(e.getPlace(), new ActionGroup() {
        @NotNull
        @Override
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          List<AnAction> result = new ArrayList<AnAction>();
          for (final Pair<File, String> homes : myJDKHomes) {
            result.add(new FileChooserAction("", null, null) {
              @Override
              protected void update(FileSystemTree fileChooser, AnActionEvent e) {
                e.getPresentation().setText(homes.getSecond(), false);
                boolean selected = false;
                VirtualFile selectedFile = fileChooser.getSelectedFile();
                if (selectedFile != null) {
                  selected = homes.getFirst().getAbsolutePath().equals(VfsUtilCore.virtualToIoFile(selectedFile).getAbsolutePath());
                }
                e.getPresentation().setIcon(selected ? AllIcons.Actions.Checked_small : null);
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

    Object source = e.getInputEvent().getSource();
    if (source instanceof Component) {
      Component component = (Component)source;
      menuComponent.show(component, 0, component.getHeight());
    }
    else {
      Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
      if (component == null) {
        return;
      }
      menuComponent
        .show(component, (component.getWidth() - menuComponent.getWidth()) / 2, (component.getHeight() - menuComponent.getHeight()) / 2);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = SystemInfo.isWindows && !myJDKHomes.isEmpty();
    if (enabled) {
      FileSystemTree tree = FileSystemTree.DATA_KEY.getData(e.getDataContext());
      if (tree instanceof FileSystemTreeImpl) {
        FileSystemTreeImpl impl = (FileSystemTreeImpl)tree;
        if (Boolean.TRUE != impl.getData(JavaSdkImpl.KEY)) {
          enabled = false;
        }
      }
      else {
        enabled = false;
      }
    }
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }
}
