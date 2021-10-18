// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
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

import static com.intellij.openapi.util.registry.Registry.is;

/**
 * Temporary utility class for migration to the new UI.
 * Do not use this class for plugin development.
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class ExperimentalUI {
  static {
    init();
  }
  public static boolean isNewUI() {
    return is("ide.experimental.ui");
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
    return false;
    //return isEnabled("ide.experimental.ui.main.toolbar");
  }

  private static boolean isEnabled(@NonNls @NotNull String key) {
    return ApplicationManager.getApplication().isEAP()
           && (isNewUI() || is(key));
  }

  private static void init() {
    Application app = ApplicationManager.getApplication();
    if (app == null) return;
    RegistryValue value = Registry.get("ide.experimental.ui");
    patchUIDefaults(value);
    IconPathPatcher iconPathPatcher = createPathPatcher();
    if (value.asBoolean()) {
      IconLoader.installPathPatcher(iconPathPatcher);
    }
    value.addListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        patchUIDefaults(value);
        if (value.asBoolean()) {
          int tabPlacement = UISettings.getInstance().getEditorTabPlacement();
          if (tabPlacement == SwingConstants.LEFT
              || tabPlacement == SwingConstants.RIGHT
              || tabPlacement == SwingConstants.BOTTOM) {
            UISettings.getInstance().setEditorTabPlacement(SwingConstants.TOP);
          }
          IconLoader.installPathPatcher(iconPathPatcher);
        } else {
          IconLoader.removePathPatcher(iconPathPatcher);
        }
      }
    }, app);
    app.getMessageBus().connect(app).subscribe(LafManagerListener.TOPIC, new LafManagerListener() {
      @Override
      public void lookAndFeelChanged(@NotNull LafManager source) {
        patchUIDefaults(value);
      }
    });
  }

  private static IconPathPatcher createPathPatcher() {
    HashMap<String, String> paths = new HashMap<>();
    paths.put("/toolwindows/toolWindowAnt.svg", "/expui/toolwindow/ant.svg");
    paths.put("/toolwindows/toolWindowBookmarks.svg", "/expui/toolwindow/bookmarks.svg");
    paths.put("/toolwindows/toolWindowBuild.svg", "/expui/toolwindow/build.svg");
    paths.put("/toolwindows/toolWindowCommit.svg", "/expui/toolwindow/commit.svg");
    paths.put("/icons/toolWindowDatabase.svg", "/expui/toolwindow/database.svg");
    paths.put("/toolwindows/toolWindowDebugger.svg", "/expui/toolwindow/debug.svg");
    paths.put("/toolwindows/documentation.svg", "/expui/toolwindow/documentation.svg");
    paths.put("/icons/toolWindowGradle.svg", "/expui/toolwindow/gradle.svg");
    paths.put("/toolwindows/toolWindowHierarchy.svg", "/expui/toolwindow/hierarchy.svg");
    paths.put("/images/toolWindowMaven.svg", "/expui/toolwindow/maven.svg");
    paths.put("/toolwindows/toolWindowProblems.svg", "/expui/toolwindow/problems.svg");
    paths.put("/toolwindows/toolWindowProfiler.svg", "/expui/toolwindow/profiler.svg");
    paths.put("/toolwindows/toolWindowProject.svg", "/expui/toolwindow/project.svg");
    paths.put("/toolwindows/toolWindowRun.svg", "/expui/toolwindow/run.svg");
    paths.put("/toolwindows/toolWindowStructure.svg", "/expui/toolwindow/structure.svg");
    paths.put("/icons/OpenTerminal_13x13.svg", "/expui/toolwindow/terminal.svg");
    paths.put("/toolwindows/toolWindowTodo.svg", "/expui/toolwindow/todo.svg");
    paths.put("/toolwindows/toolWindowChanges.svg", "/expui/toolwindow/vcs.svg");
    paths.put("/toolwindows/webToolWindow.svg", "/expui/toolwindow/web.svg");
    return new IconPathPatcher() {
      @Override
      public @Nullable String patchPath(@NotNull String path,
                                        @Nullable ClassLoader classLoader) {
        return paths.get(path.replace("_dark.svg", ".svg"));
      }
    };
  }

  private static void patchUIDefaults(RegistryValue value) {
    if (!value.asBoolean()) return;
    UIDefaults defaults = UIManager.getDefaults();
    setUIProperty("EditorTabs.underlineArc", 4, defaults);
    EditorColorsScheme editorColorScheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color tabsHover = ColorUtil.mix(JBColor.PanelBackground, editorColorScheme.getDefaultBackground(), 0.5);
    setUIProperty("EditorTabs.hoverInactiveBackground", tabsHover, defaults);

    if (is("ide.experimental.ui.inter.font") && SystemInfo.isJetBrainsJvm) {
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

      if (JBColor.isBright()) {
        Color menuBg = new ColorUIResource(0x242933);
        Color menuFg = new ColorUIResource(0xFFFFFF);
        setUIProperty("PopupMenu.background", menuBg, defaults);
        setUIProperty("MenuItem.background", menuBg, defaults);
        setUIProperty("MenuItem.foreground", menuFg, defaults);
        setUIProperty("Menu.background", menuBg, defaults);
        setUIProperty("Menu.foreground", menuFg, defaults);
        setUIProperty("CheckBoxMenuItem.acceleratorForeground", menuFg, defaults);
        setUIProperty("Menu.acceleratorForeground", menuFg, defaults);
        setUIProperty("MenuItem.acceleratorForeground", menuFg, defaults);
        setUIProperty("RadioButtonMenuItem.acceleratorForeground", menuFg, defaults);
      }
    }
}
