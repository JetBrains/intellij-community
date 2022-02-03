// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileChooserAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.NlsSafe;
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

public class JdkPopupAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(JdkPopupAction.class);

  public JdkPopupAction() {
    super(JavaBundle.message("action.text.show.quick.list"), "", AllIcons.General.AddJdk);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = isEnabledInCurrentOS();
    if (enabled) {
      FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
      if (tree == null || Boolean.TRUE != tree.getData(JavaSdkImpl.KEY)) {
        enabled = false;
      }
    }
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final JComponent component;
    final boolean showInMiddle;
    InputEvent inputEvent = e.getInputEvent();
    Object source = inputEvent != null ? inputEvent.getSource() : null;
    if (source instanceof JComponent) {
      component = (JComponent)source;
      showInMiddle = false;
    }
    else {
      Component c = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
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
    }, JavaBundle.message("progress.title.looking.for.jdk.locations"), false, e.getProject(), component);
  }

  private static boolean isEnabledInCurrentOS() {
    return SystemInfo.isWindows;
  }

  private static void showPopupMenu(AnActionEvent e,
                                    List<? extends Pair<File, String>> jdkLocations,
                                    boolean showInMiddle,
                                    JComponent component) {
    ActionPopupMenu menu =
      ActionManager.getInstance().createActionPopupMenu(e.getPlace(), new ActionGroup() {
        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
          List<AnAction> result = new ArrayList<>();
          for (final Pair<File, @NlsSafe String> homes : jdkLocations) {
            result.add(new FileChooserAction("", null, null) {
              @Override
              protected void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
                e.getPresentation().setText(homes.getSecond(), false);
                boolean selected = false;
                VirtualFile selectedFile = fileChooser.getSelectedFile();
                if (selectedFile != null) {
                  selected = homes.getFirst().getAbsolutePath().equals(VfsUtilCore.virtualToIoFile(selectedFile).getAbsolutePath());
                }
                e.getPresentation().setIcon(selected ? AllIcons.Actions.Forward : null);
              }

              @Override
              protected void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
                fileChooser.select(VfsUtil.findFileByIoFile(homes.getFirst(), true), null);
              }
            });
          }
          return result.toArray(AnAction.EMPTY_ARRAY);
        }
      });
    JPopupMenu menuComponent = menu.getComponent();
    if (showInMiddle) {
      menuComponent
        .show(component, (component.getWidth() - menuComponent.getWidth()) / 2,
              (component.getHeight() - menuComponent.getHeight()) / 2);
    }
    else {
      JBPopupMenu.showBelow(component, menuComponent);
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
            stringBuilder.append(line, pos + 7, line.length() - 1);
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
