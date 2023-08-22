// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

final class DumpInspectionDescriptionsAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(DumpInspectionDescriptionsAction.class);

  DumpInspectionDescriptionsAction() {
    super(ActionsBundle.messagePointer("action.DumpInspectionDescriptionsAction.text"));
  }


  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent event) {
    InspectionProfile profile = InspectionProfileManager.getInstance().getCurrentProfile();
    Collection<String> classes = new TreeSet<>();
    Map<String, Collection<String>> groups = new TreeMap<>();

    String tempDirectory = FileUtil.getTempDirectory();
    File descDirectory = new File(tempDirectory, "inspections");
    if (!descDirectory.mkdirs() && !descDirectory.isDirectory()) {
      LOG.error("Unable to create directory: " + descDirectory.getAbsolutePath());
      return;
    }

    for (InspectionToolWrapper<?, ?> toolWrapper : profile.getInspectionTools(null)) {
      classes.add(getInspectionClass(toolWrapper).getName());

      String group = getGroupName(toolWrapper);
      Collection<String> names = groups.get(group);
      if (names == null) groups.put(group, (names = new TreeSet<>()));
      names.add(toolWrapper.getShortName());

      InputStream stream = getDescriptionStream(toolWrapper);
      if (stream != null) {
        doDump(descDirectory.toPath().resolve(toolWrapper.getShortName() + ".html"), new Processor() {
          @Override public void process(BufferedWriter writer) throws Exception {
            writer.write(ResourceUtil.loadText(stream));
          }
        });
      }
    }
    doNotify("Inspection descriptions dumped to\n" + descDirectory.getAbsolutePath());

    Path fqnListFile = Path.of(tempDirectory, "inspection_fqn_list.txt");
    boolean fqnOk = doDump(fqnListFile, new Processor() {
      @Override public void process(BufferedWriter writer) throws Exception {
        for (String name : classes) {
          writer.write(name);
          writer.newLine();
        }
      }
    });
    if (fqnOk) {
      doNotify("Inspection class names dumped to\n" + fqnListFile);
    }

    Path groupsFile = Path.of(tempDirectory, "inspection_groups.txt");
    final boolean groupsOk = doDump(groupsFile, new Processor() {
      @Override public void process(BufferedWriter writer) throws Exception {
        for (Map.Entry<String, Collection<String>> entry : groups.entrySet()) {
          writer.write(entry.getKey());
          writer.write(':');
          writer.newLine();
          for (String name : entry.getValue()) {
            writer.write("  ");
            writer.write(name);
            writer.newLine();
          }
        }
      }
    });
    if (groupsOk) {
      doNotify("Inspection groups dumped to\n" + fqnListFile);
    }
  }

  private static Class<?> getInspectionClass(InspectionToolWrapper<?, ?> toolWrapper) {
    return toolWrapper instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)toolWrapper).getTool().getClass() : toolWrapper.getClass();
  }

  private static String getGroupName(InspectionToolWrapper<?, ?> toolWrapper) {
    final String name = toolWrapper.getGroupDisplayName();
    return StringUtil.isEmptyOrSpaces(name) ? "General" : name;
  }

  private static InputStream getDescriptionStream(InspectionToolWrapper<?, ?> toolWrapper) {
    Class<?> aClass = getInspectionClass(toolWrapper);
    return ResourceUtil.getResourceAsStream(aClass.getClassLoader(), "inspectionDescriptions", toolWrapper.getShortName() + ".html");
  }

  private interface Processor {
    void process(BufferedWriter writer) throws Exception;
  }

  private static boolean doDump(Path file, Processor processor) {
    try (BufferedWriter writer = Files.newBufferedWriter(file)) {
      processor.process(writer);
      return true;
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
  }

  private static void doNotify(final String message) {
    Notifications.Bus.notify(new Notification("Actions", "Inspection descriptions dumped", message, NotificationType.INFORMATION));
  }
}