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
package com.intellij.ide;

import com.intellij.Patches;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.Alarm;
import com.intellij.util.BitUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.lang.reflect.Field;
import java.util.EventListener;

/**
 * This class listens event from ProjectManager and cleanup some
 * internal Swing references.
 *
 * @author Vladimir Kondratyev
 */
public final class SwingCleanuper {
  private final Alarm myAlarm;

  public SwingCleanuper(@NotNull Application application) {
    myAlarm = new Alarm(application);
    application.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
        @Override
        public void projectOpened(final Project project) {
          myAlarm.cancelAllRequests();
        }
        // Swing keeps references to the last focused component inside DefaultKeyboardFocusManager.realOppositeComponent
        // which is used to compose next focus event. Actually this component could be an editors or a tool window. To fix this
        // memory leak we (if the project was closed and a new one was not opened yet) request focus to the status bar and after
        // the focus events have passed the queue, we put 'null' to the DefaultKeyboardFocusManager.realOppositeComponent field.
        @Override
        public void projectClosed(final Project project){
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(
            () -> {
              // request focus into some focusable component inside IdeFrame
              final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
              IdeFrameImpl frame = (IdeFrameImpl)(window instanceof IdeFrameImpl ? window :
                                                  SwingUtilities.getAncestorOfClass(IdeFrameImpl.class, window));
              if (frame != null) {
                final Application app = ApplicationManager.getApplication();
                if (app != null && app.isActive()) {
                  StatusBar statusBar = frame.getStatusBar();
                  if (statusBar != null) {
                    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
                      IdeFocusManager.getGlobalInstance().requestFocus((JComponent)statusBar, true);
                    });
                  }
                }
              }
            },
            2500
          );
        }
      }
    );

    if (SystemInfo.isMac) {
      Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
        if (!Registry.is("ide.mac.fix.accessibleLeak")) return;

        HierarchyEvent he = (HierarchyEvent)event;
        if (BitUtil.isSet(he.getChangeFlags(), HierarchyEvent.SHOWING_CHANGED)) {
          if (he.getComponent() != null && !he.getComponent().isShowing()) {
            Component c = he.getComponent();
            if (c instanceof JTextComponent) {
              JTextComponent textComponent = (JTextComponent)c;

              CaretListener[] carets = textComponent.getListeners(CaretListener.class);
              for (CaretListener each : carets) {
                if (isCAccessibleListener(each)) {
                  textComponent.removeCaretListener(each);
                }
              }

              Document document = textComponent.getDocument();
              if (document instanceof AbstractDocument) {
                DocumentListener[] documentListeners = ((AbstractDocument)document).getDocumentListeners();
                for (DocumentListener each : documentListeners) {
                  if (isCAccessibleListener(each)) {
                    document.removeDocumentListener(each);
                  }
                }
              }
            }
            else if (c instanceof JProgressBar) {
              JProgressBar bar = (JProgressBar)c;
              ChangeListener[] changeListeners = bar.getChangeListeners();
              for (ChangeListener each : changeListeners) {
                if (isCAccessibleListener(each)) {
                  bar.removeChangeListener(each);
                }
              }
            }
            else if (c instanceof JSlider) {
              JSlider slider = (JSlider)c;
              ChangeListener[] changeListeners = slider.getChangeListeners();
              for (ChangeListener each : changeListeners) {
                if (isCAccessibleListener(each)) {
                  slider.removeChangeListener(each);
                }
              }
            }

            Field nativeAXResource_Field = null;
            Field accessibleContext_Field = null;
            try {
              nativeAXResource_Field = ReflectionUtil.findField(AccessibleContext.class, Object.class, "nativeAXResource");
              accessibleContext_Field = ReflectionUtil.findField(Component.class, AccessibleContext.class, "accessibleContext");
            }
            catch (NoSuchFieldException ignored) {
            }

            if (accessibleContext_Field != null) {
              try {
                // Component's AccessibleContext is not necessarily initialized. In this case we don't want to force its creation.
                // So, first we check the Component.accessibleContext field. The field has a protected access and it's a common
                // Swing pattern to set it in the Component.getAccessibleContext() method when it's overriden by a subclass
                // (and we're to follow it).
                AccessibleContext ac = (AccessibleContext)accessibleContext_Field.get(c);
                if (ac != null) {
                  // The getter may have a side effect, so call it to get the up-to-date context.
                  ac = c.getAccessibleContext();
                  if (ac != null) {
                    Object resource = nativeAXResource_Field.get(ac);
                    if (resource != null && isCAccessible(resource)) {
                      Field accessible = ReflectionUtil.findField(resource.getClass(), Accessible.class, "accessible");
                      accessible.set(resource, null);
                    }
                  }
                }
              }
              catch (Exception ignored) {
              }
            }
          }
        }
      }, AWTEvent.HIERARCHY_EVENT_MASK);
    }
  }

  private static boolean isCAccessible(Object resource) {
    final String name = resource.getClass().getName();
    return isCAccessible(name);
  }

  static boolean isCAccessible(String name) {
    return name.equals("apple.awt.CAccessible") || name.equals("sun.lwawt.macosx.CAccessible");
  }

  private static boolean isCAccessibleListener(EventListener listener) {
    return listener != null && listener.toString().contains("AXTextChangeNotifier");
  }

}
