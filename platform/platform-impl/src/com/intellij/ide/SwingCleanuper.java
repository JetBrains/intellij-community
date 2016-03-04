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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EventListener;

/**
 * This class listens event from ProjectManager and cleanup some
 * internal Swing references.
 *
 * @author Vladimir Kondratyev
 */
public final class SwingCleanuper implements ApplicationComponent{
  private final Alarm myAlarm;

  public SwingCleanuper(@NotNull Application application, ProjectManager projectManager) {
    myAlarm = new Alarm(application);
    projectManager.addProjectManagerListener(new ProjectManagerAdapter(){
        public void projectOpened(final Project project) {
          myAlarm.cancelAllRequests();
        }
        // Swing keeps references to the last focused component inside DefaultKeyboardFocusManager.realOppositeComponent
        // which is used to compose next focus event. Actually this component could be an editors or a tool window. To fix this
        // memory leak we (if the project was closed and a new one was not opened yet) request focus to the status bar and after
        // the focus events have passed the queue, we put 'null' to the DefaultKeyboardFocusManager.realOppositeComponent field.
        public void projectClosed(final Project project){
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(
            new Runnable() {
              public void run() {
                // request focus into some focusable component inside IdeFrame
                final IdeFrameImpl frame;
                final Window window=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                if(window instanceof IdeFrameImpl){
                  frame=(IdeFrameImpl)window;
                }else{
                  frame=(IdeFrameImpl)SwingUtilities.getAncestorOfClass(IdeFrameImpl.class,window);
                }
                if(frame!=null){
                  final Application app = ApplicationManager.getApplication();
                  if (app != null && app.isActive()) {
                    StatusBar statusBar = frame.getStatusBar();
                    if (statusBar != null) ((JComponent)statusBar).requestFocus();
                  }
                }

                //noinspection SSBasedInspection
                SwingUtilities.invokeLater(
                  new Runnable() {
                    public void run() {

                      // KeyboardFocusManager.newFocusOwner
                      ReflectionUtil.resetStaticField(KeyboardFocusManager.class, "newFocusOwner");

                      // Clear "realOppositeComponent", "realOppositeWindow"
                      final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                      resetField(focusManager, Component.class, "realOppositeComponent");
                      resetField(focusManager, Window.class, "realOppositeWindow");


                      // Memory leak on static field in BasicPopupMenuUI

                      try {
                        Object helperObject = ReflectionUtil.getStaticFieldValue(BasicPopupMenuUI.class, Object.class, "menuKeyboardHelper");
                        if (helperObject != null) {
                          resetField(helperObject, Component.class, "lastFocused");
                        }
                      }
                      catch (Exception e) {
                        // Ignore
                      }

                      // Memory leak on javax.swing.TransferHandler$SwingDragGestureRecognizer.component

                      try{
                        DragGestureRecognizer recognizer = ReflectionUtil.getStaticFieldValue(TransferHandler.class, DragGestureRecognizer.class, "recognizer");

                        if (recognizer != null) { // that is memory leak
                          recognizer.setComponent(null);
                        }
                      }
                      catch (Exception e){
                        // Ignore
                      }
                      try {
                        fixJTextComponentMemoryLeak();
                      }
                      catch(Exception e) {
                        // Ignore
                      }

                      focusManager.setGlobalCurrentFocusCycleRoot(null); //Remove focus leaks

                      try {
                        final Method m = ReflectionUtil.getDeclaredMethod(KeyboardFocusManager.class,"setGlobalFocusOwner", Component.class);
                        m.invoke(focusManager, new Object[]{null});
                      }
                      catch (Exception e) {
                        // Ignore
                      }

                      ReflectionUtil.resetStaticField(KeyboardFocusManager.class, "newFocusOwner");
                      ReflectionUtil.resetStaticField(KeyboardFocusManager.class, "permanentFocusOwner");
                      ReflectionUtil.resetStaticField(KeyboardFocusManager.class, "currentFocusCycleRoot");
                    }
                  }
                );
              }
            },
            2500
          );
        }
      }
    );

    if (SystemInfo.isMac) {
      Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {

        private Field nativeAXResource_Field = null;
        private Field accessibleContext_Field = null;

        {
          try {
            nativeAXResource_Field = ReflectionUtil.findField(AccessibleContext.class, Object.class, "nativeAXResource");
            accessibleContext_Field = ReflectionUtil.findField(Component.class, AccessibleContext.class, "accessibleContext");
          }
          catch (NoSuchFieldException ignored) {
          }
        }

        @Override
        public void eventDispatched(AWTEvent event) {
          if (!Registry.is("ide.mac.fix.accessibleLeak")) return;

          HierarchyEvent he = (HierarchyEvent)event;
          if ((he.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) > 0) {
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

              if (accessibleContext_Field != null && nativeAXResource_Field != null) {
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
                } catch (Exception ignored) {
                }
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

  private static void resetField(Object object, Class type, @NonNls String name) {
    try {
      ReflectionUtil.resetField(object, ReflectionUtil.findField(object.getClass(), type, name));
    }
    catch (Exception e) {
      // Ignore
    }
  }

  public final void disposeComponent(){}

  @NotNull
  public final String getComponentName(){
    return "SwingCleanuper";
  }

  public final void initComponent() { }

  private static void fixJTextComponentMemoryLeak() {
    final JTextComponent component = ReflectionUtil.getStaticFieldValue(JTextComponent.class, JTextComponent.class, "focusedComponent");
    if (component != null && !component.isDisplayable()){
      ReflectionUtil.resetField(JTextComponent.class, JTextComponent.class, "focusedComponent");
    }
  }

}
