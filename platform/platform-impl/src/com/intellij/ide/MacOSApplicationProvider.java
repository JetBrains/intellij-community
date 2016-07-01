/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide;

import com.apple.eawt.Application;
import com.intellij.ide.actions.AboutAction;
import com.intellij.ide.actions.ExitAction;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.ide.actions.ShowSettingsAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Component;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class MacOSApplicationProvider implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(MacOSApplicationProvider.class);
  private static final AtomicBoolean ENABLED = new AtomicBoolean(true);
  private static final Callback IMPL = new Callback() {
    @SuppressWarnings("unused")
    public void callback(ID self, String selector) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        ActionManagerEx am = ActionManagerEx.getInstanceEx();
        MouseEvent me = new MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false);
        am.tryToExecute(am.getAction("CheckForUpdate"), me, null, null, false);
      });
    }
  };
  private static final String GENERIC_RGB_PROFILE_PATH = "/System/Library/ColorSync/Profiles/Generic RGB Profile.icc";

  private final ColorSpace genericRgbColorSpace;
  
  public static MacOSApplicationProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(MacOSApplicationProvider.class);
  }

  public MacOSApplicationProvider() {
    if (SystemInfo.isMac) {
      try {
        Worker.initMacApplication();
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
      genericRgbColorSpace = initializeNativeColorSpace();
    }
    else {
      genericRgbColorSpace = null;
    }
  }

  private static ColorSpace initializeNativeColorSpace() {
    try (InputStream is = new FileInputStream(GENERIC_RGB_PROFILE_PATH)) {
      ICC_Profile profile = ICC_Profile.getInstance(is);
      return new ICC_ColorSpace(profile);
    }
    catch (Throwable e) {
      LOG.warn("Couldn't load generic RGB color profile", e);
      return null;
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "MACOSApplicationProvider";
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() { }

  @Nullable
  public ColorSpace getGenericRgbColorSpace() {
    return genericRgbColorSpace;
  }

  private static class Worker {
    public static void initMacApplication() {
      Application application = Application.getApplication();
      application.setAboutHandler(event -> AboutAction.perform(getProject()));
      application.setPreferencesHandler(event -> {
        Project project = getNotNullProject();
        submit(() -> ShowSettingsAction.perform(project));
      });
      application.setQuitHandler((event, response) -> {
        submit(ExitAction::perform);
        response.cancelQuit();
      });
      application.setOpenFileHandler(event -> {
        Project project = getProject();
        List<File> list = event.getFiles();
        LOG.debug("MacMenu: files found ", list.size());
        if (list.isEmpty()) return;
        File file = list.get(0);
        submit(() -> {
          LOG.debug("MacMenu: try to open file");
          if (ProjectUtil.openOrImport(file.getAbsolutePath(), project, true) != null) {
            LOG.debug("MacMenu: load project for ", file);
            IdeaApplication.getInstance().setPerformProjectLoad(false);
            return;
          }
          LOG.debug("MacMenu: project = ", project);
          if (project != null && file.exists()) {
            LOG.debug("MacMenu: open file ", file);
            OpenFileAction.openFile(file.getAbsolutePath(), project);
          }
        });
      });
      installAutoUpdateMenu();
    }

    private static void installAutoUpdateMenu() {
      ID pool = Foundation.invoke("NSAutoreleasePool", "new");

      ID app = Foundation.invoke("NSApplication", "sharedApplication");
      ID menu = Foundation.invoke(app, Foundation.createSelector("menu"));
      ID item = Foundation.invoke(menu, Foundation.createSelector("itemAtIndex:"), 0);
      ID appMenu = Foundation.invoke(item, Foundation.createSelector("submenu"));

      ID checkForUpdatesClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSMenuItem"), "NSCheckForUpdates");
      Foundation.addMethod(checkForUpdatesClass, Foundation.createSelector("checkForUpdates"), IMPL, "v");

      Foundation.registerObjcClassPair(checkForUpdatesClass);

      ID checkForUpdates = Foundation.invoke("NSCheckForUpdates", "alloc");
      Foundation.invoke(checkForUpdates, Foundation.createSelector("initWithTitle:action:keyEquivalent:"),
                        Foundation.nsString("Check for Updates..."),
                        Foundation.createSelector("checkForUpdates"), Foundation.nsString(""));
      Foundation.invoke(checkForUpdates, Foundation.createSelector("setTarget:"), checkForUpdates);

      Foundation.invoke(appMenu, Foundation.createSelector("insertItem:atIndex:"), checkForUpdates, 1);
      Foundation.invoke(checkForUpdates, Foundation.createSelector("release"));

      Foundation.invoke(pool, Foundation.createSelector("release"));
    }

    @SuppressWarnings("deprecation")
    private static Project getProject() {
      return CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    }

    @NotNull
    private static Project getNotNullProject() {
      Project project = getProject();
      return project != null ? project : ProjectManager.getInstance().getDefaultProject();
    }

    private static void submit(@NotNull Runnable task) {
      LOG.debug("MacMenu: on EDT = ", SwingUtilities.isEventDispatchThread(), "; ENABLED = ", ENABLED.get());
      if (!ENABLED.get()) return;

      Component component = IdeFocusManager.getGlobalInstance().getFocusOwner();
      if (component == null || IdeKeyEventDispatcher.isModalContext(component)) return;

      ENABLED.set(false);
      TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
        try {
          task.run();
        }
        finally {
          ENABLED.set(true);
        }
      });
    }
  }
}
