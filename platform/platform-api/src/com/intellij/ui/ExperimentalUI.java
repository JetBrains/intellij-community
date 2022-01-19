// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.EarlyAccessRegistryManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary utility class for migration to the new UI.
 * Do not use this class for plugin development.
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class ExperimentalUI {
  private static final AtomicBoolean isIconPatcherSet = new AtomicBoolean();
  private static final IconPathPatcher iconPathPatcher = createPathPatcher();
  private static final String KEY = "ide.experimental.ui";

  public static boolean isNewUI() {
    return EarlyAccessRegistryManager.INSTANCE.getBoolean(KEY);
  }

  public static boolean isNewToolWindowsStripes() {
    return isEnabled("ide.experimental.ui.toolwindow.stripes");
  }

  public static boolean isNewEditorTabs() {
    return isEnabled("ide.experimental.ui.editor.tabs");
  }

  public static boolean isNewVcsBranchPopup() {
    return isEnabled("ide.experimental.ui.vcs.branch.popup");
  }

  public static boolean isNewToolbar() {
    return isEnabled("ide.experimental.ui.main.toolbar");
  }

  private static boolean isEnabled(@NonNls @NotNull String key) {
    return ApplicationManager.getApplication().isEAP() && (isNewUI() || EarlyAccessRegistryManager.INSTANCE.getBoolean(key));
  }

  @SuppressWarnings("unused")
  private static final class NewUiRegistryListener implements RegistryValueListener {
    @Override
    public void afterValueChanged(@NotNull RegistryValue value) {
      if (!value.getKey().equals(KEY)) {
        return;
      }

      boolean isEnabled = value.asBoolean();

      patchUIDefaults(isEnabled);
      if (isEnabled) {
        int tabPlacement = UISettings.getInstance().getEditorTabPlacement();
        if (tabPlacement == SwingConstants.LEFT
            || tabPlacement == SwingConstants.RIGHT
            || tabPlacement == SwingConstants.BOTTOM) {
          UISettings.getInstance().setEditorTabPlacement(SwingConstants.TOP);
        }

        if (isIconPatcherSet.compareAndSet(false, true)) {
          IconLoader.installPathPatcher(iconPathPatcher);
        }
      }
      else if (isIconPatcherSet.compareAndSet(true, false)) {
        IconLoader.removePathPatcher(iconPathPatcher);
      }
    }
  }

  @SuppressWarnings("unused")
  private static final class NewUiLafManagerListener implements LafManagerListener {
    @Override
    public void lookAndFeelChanged(@NotNull LafManager source) {
      if (isNewUI()) {
        if (isIconPatcherSet.compareAndSet(false, true)) {
          IconLoader.installPathPatcher(iconPathPatcher);
        }
        patchUIDefaults(true);
      }
    }
  }

  private static IconPathPatcher createPathPatcher() {
    Map<String, String> paths = new HashMap<>();
    paths.put("actions/collapseall.svg", "/expui/general/collapseAll.svg");
    paths.put("actions/expandall.svg", "/expui/general/expandAll.svg");
    paths.put("actions/back.svg", "/expui/general/left.svg");
    paths.put("actions/forward.svg", "/expui/general/right.svg");
    paths.put("actions/previousOccurence.svg", "/expui/general/up.svg");
    paths.put("actions/nextOccurence.svg", "/expui/general/down.svg");
    paths.put("actions/refresh.svg", "/expui/general/refresh.svg");
    paths.put("general/remove.svg", "/expui/general/remove.svg");
    paths.put("nodes/class.svg", "/expui/nodes/class.svg");
    paths.put("nodes/folder.svg", "/expui/nodes/folder.svg");
    paths.put("nodes/interface.svg", "/expui/nodes/interface.svg");
    paths.put("nodes/ppLib.svg", "/expui/nodes/library.svg");
    paths.put("modules/excludeRoot.svg", "/expui/nodes/excludeRoot.svg");
    paths.put("modules/sourceRoot.svg", "/expui/nodes/sourceRoot.svg");
    paths.put("modules/testRoot.svg", "/expui/nodes/testRoot.svg");
    paths.put("toolwindows/toolWindowAnt.svg", "/expui/toolwindow/ant.svg");
    paths.put("toolwindows/toolWindowBookmarks.svg", "/expui/toolwindow/bookmarks.svg");
    paths.put("toolwindows/toolWindowBuild.svg", "/expui/toolwindow/build.svg");
    paths.put("toolwindows/toolWindowCommit.svg", "/expui/toolwindow/commit.svg");
    paths.put("icons/toolWindowDatabase.svg", "/expui/toolwindow/database.svg");
    paths.put("toolwindows/toolWindowDebugger.svg", "/expui/toolwindow/debug.svg");
    paths.put("toolwindows/toolWindowModuleDependencies.svg", "/expui/toolwindow/dependencies.svg");
    paths.put("toolwindows/documentation.svg", "/expui/toolwindow/documentation.svg");
    paths.put("icons/toolWindowEndpoints.svg", "/expui/toolwindow/endpoints.svg");
    paths.put("icons/toolWindowGradle.svg", "/expui/toolwindow/gradle.svg");
    paths.put("toolwindows/toolWindowHierarchy.svg", "/expui/toolwindow/hierarchy.svg");
    paths.put("img/featureTrainerToolWindow.svg", "/expui/toolwindow/learn.svg");
    paths.put("images/toolWindowMaven.svg", "/expui/toolwindow/maven.svg");
    paths.put("toolwindows/toolWindowProblems.svg", "/expui/toolwindow/problems.svg");
    paths.put("toolwindows/toolWindowProblemsEmpty.svg", "/expui/toolwindow/problems.svg");
    paths.put("toolwindows/toolWindowProfiler.svg", "/expui/toolwindow/profiler.svg");
    paths.put("toolwindows/toolWindowProject.svg", "/expui/toolwindow/project.svg");
    paths.put("org/jetbrains/plugins/github/pullRequestsToolWindow.svg", "/expui/toolwindow/pullRequests.svg");
    paths.put("toolwindows/toolWindowRun.svg", "/expui/toolwindow/run.svg");
    paths.put("toolwindows/toolWindowServices.svg", "/expui/toolwindow/services.svg");
    paths.put("toolwindows/toolWindowStructure.svg", "/expui/toolwindow/structure.svg");
    paths.put("icons/OpenTerminal_13x13.svg", "/expui/toolwindow/terminal.svg");
    paths.put("toolwindows/toolWindowTodo.svg", "/expui/toolwindow/todo.svg");
    paths.put("toolwindows/toolWindowChanges.svg", "/expui/toolwindow/vcs.svg");
    paths.put("toolwindows/webToolWindow.svg", "/expui/toolwindow/web.svg");
    paths.put("toolwindows/toolWindowFind.svg", "/expui/toolwindow/find.svg");
    paths.put("actions/more.svg", "/expui/general/moreVertical.svg");
    paths.put("actions/moreHorizontal.svg", "/expui/general/moreHorizontal.svg");
    paths.put("general/hideToolWindow.svg", "/expui/general/close.svg");
    paths.put("actions/find.svg", "/expui/general/search.svg");
    paths.put("general/gearPlain.svg", "/expui/general/settings.svg");
    paths.put("general/chevron-down.svg", "expui/general/chevronDown.svg");
    paths.put("general/chevron-left.svg", "expui/general/chevronLeft.svg");
    paths.put("general/chevron-right.svg", "expui/general/chevronRight.svg");
    paths.put("general/chevron-up.svg", "expui/general/chevronUp.svg");
    paths.put("vcs/branch.svg", "expui/toolwindow/vcs.svg");
    paths.put("icons/Docker.svg", "expui/fileTypes/docker.svg");
    paths.put("icons/DockerFile_1.svg", "expui/fileTypes/docker.svg");
    paths.put("icons/gradle.svg", "expui/fileTypes/gradle.svg");
    paths.put("icons/gradleFile.svg", "expui/fileTypes/gradle.svg");
    paths.put("nodes/ideaModule.svg", "expui/fileTypes/ideaModule.svg");
    paths.put("nodes/editorconfig.svg", "/expui/fileTypes/editorConfig.svg");
    paths.put("fileTypes/css.svg", "expui/fileTypes/css.svg");
    paths.put("fileTypes/html.svg", "expui/fileTypes/html.svg");
    paths.put("fileTypes/java.svg", "expui/fileTypes/java.svg");
    paths.put("fileTypes/javaScript.svg", "expui/fileTypes/javascript.svg");
    paths.put("fileTypes/json.svg", "expui/fileTypes/json.svg");
    paths.put("fileTypes/manifest.svg", "expui/fileTypes/manifest.svg");
    paths.put("icons/php-icon.svg", "expui/fileTypes/php.svg");
    paths.put("fileTypes/properties.svg", "expui/fileTypes/properties.svg");
    paths.put("icons/ruby/ruby.svg", "expui/fileTypes/ruby.svg");
    paths.put("icons/ruby/ruby_file.svg", "expui/fileTypes/ruby.svg");
    paths.put("icons/sql.svg", "expui/fileTypes/sql.svg");
    paths.put("fileTypes/text.svg", "expui/fileTypes/text.svg");
    paths.put("icons/fileTypes/TypeScriptFile.svg", "expui/fileTypes/typescript.svg");
    paths.put("fileTypes/unknown.svg", "expui/fileTypes/unknown.svg");
    paths.put("fileTypes/xml.svg", "expui/fileTypes/xml.svg");
    paths.put("fileTypes/yaml.svg", "expui/fileTypes/yaml.svg");
    paths.put("fileTypes/archive.svg", "/expui/fileTypes/archive.svg");
    paths.put("org/intellij/images/icons/ImagesFileType.svg", "/expui/fileTypes/image.svg");
    paths.put("org/jetbrains/plugins/sass/sass.svg", "/expui/fileTypes/scss.svg");
    paths.put("icons/shFile.svg", "/expui/fileTypes/shell.svg");
    paths.put("icons/MarkdownPlugin.svg", "/expui/fileTypes/markdown.svg");
    paths.put("actions/execute.svg", "expui/run/run.svg");
    paths.put("actions/startDebugger.svg", "expui/run/debug.svg");
    paths.put("actions/restart.svg", "expui/run/restart.svg");
    paths.put("actions/suspend.svg", "expui/run/stop.svg");
    return new IconPathPatcher() {
      @Override
      public @Nullable String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
        return paths.get(Strings.trimStart(path, "/"));
      }

      @Override
      public @Nullable ClassLoader getContextClassLoader(@NotNull String path,
                                                         @Nullable ClassLoader originalClassLoader) {
        return getClass().getClassLoader();
      }
    };
  }

  private static void patchUIDefaults(boolean isNewUiEnabled) {
    if (!isNewUiEnabled) {
      return;
    }

    UIDefaults defaults = UIManager.getDefaults();
    setUIProperty("EditorTabs.underlineArc", 4, defaults);
    setUIProperty("ToolWindow.Button.selectedBackground", new ColorUIResource(0x3573f0), defaults);
    setUIProperty("ToolWindow.Button.selectedForeground", new ColorUIResource(0xffffff), defaults);
    EditorColorsScheme editorColorScheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color tabsHover = ColorUtil.mix(JBColor.PanelBackground, editorColorScheme.getDefaultBackground(), 0.5);
    setUIProperty("EditorTabs.hoverInactiveBackground", tabsHover, defaults);

    if (SystemInfo.isJetBrainsJvm && EarlyAccessRegistryManager.INSTANCE.getBoolean("ide.experimental.ui.inter.font")) {
      installInterFont();
    }
  }

  private static void setUIProperty(String key, Object value, UIDefaults defaults) {
    defaults.remove(key);
    defaults.put(key, value);
  }

  private static void installInterFont() {
    UIDefaults defaults = UIManager.getDefaults();
    List<String> keysToPatch = Arrays.asList("CheckBoxMenuItem.acceleratorFont",
                                             "CheckBoxMenuItem.font",
                                             "Menu.acceleratorFont",
                                             "Menu.font",
                                             //"MenuBar.font",
                                             "MenuItem.acceleratorFont",
                                             "MenuItem.font",
                                             "PopupMenu.font",
                                             "RadioButtonMenuItem.acceleratorFont",
                                             "RadioButtonMenuItem.font");
    for (String key : keysToPatch) {
      Font font = UIManager.getFont(key);
      Font inter = new FontUIResource("Inter", font.getStyle(), font.getSize());
      defaults.put(key, inter);
    }

    //if (JBColor.isBright()) {
    //  Color menuBg = new ColorUIResource(0x242933);
    //  Color menuFg = new ColorUIResource(0xFFFFFF);
    //  setUIProperty("PopupMenu.background", menuBg, defaults);
    //  setUIProperty("MenuItem.background", menuBg, defaults);
    //  setUIProperty("MenuItem.foreground", menuFg, defaults);
    //  setUIProperty("Menu.background", menuBg, defaults);
    //  setUIProperty("Menu.foreground", menuFg, defaults);
    //  setUIProperty("CheckBoxMenuItem.acceleratorForeground", menuFg, defaults);
    //  setUIProperty("Menu.acceleratorForeground", menuFg, defaults);
    //  setUIProperty("MenuItem.acceleratorForeground", menuFg, defaults);
    //  setUIProperty("RadioButtonMenuItem.acceleratorForeground", menuFg, defaults);
    //}
  }
}
