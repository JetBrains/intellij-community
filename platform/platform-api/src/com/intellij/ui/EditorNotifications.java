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
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class EditorNotifications extends AbstractProjectComponent {

  private static final ExtensionPointName<Provider> EXTENSION_POINT_NAME = new ExtensionPointName<Provider>("com.intellij.editorNotificationProvider");

  public interface Provider<T extends JComponent> {


    Key<T> getKey();

    @Nullable
    T createNotificationPanel(VirtualFile file);

  }

  public static EditorNotifications getInstance(Project project) {
    return project.getComponent(EditorNotifications.class);
  }

  private final NotNullLazyValue<Provider[]> PROVIDERS = new NotNullLazyValue<Provider[]>() {
    @NotNull
    @Override
    protected Provider[] compute() {
      return Extensions.getExtensions(EXTENSION_POINT_NAME, myProject);
    }
  };

  private final FileEditorManager myFileEditorManager;

  public EditorNotifications(final Project project, FileEditorManager fileEditorManager) {
    super(project);
    myFileEditorManager = fileEditorManager;
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(FileEditorManager source, VirtualFile file) {
        updateNotifications(file);
      }
    });
  }

  public void updateNotifications(final VirtualFile file) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        FileEditor[] editors = myFileEditorManager.getEditors(file);
        for (FileEditor editor : editors) {
          for (Provider<?> provider : PROVIDERS.getValue()) {
            JComponent component = provider.createNotificationPanel(file);
            Key<? extends JComponent> key = provider.getKey();
            updateNotification(editor, key, component);
          }
        }
      }
    });
  }

  public void updateAllNotifications() {
    VirtualFile[] files = myFileEditorManager.getOpenFiles();
    for (VirtualFile file : files) {
      updateNotifications(file);
    }
  }

  private void updateNotification(FileEditor editor, Key<? extends JComponent> key, @Nullable JComponent component) {
    JComponent old = editor.getUserData(key);
    if (old != null) {
      myFileEditorManager.removeTopComponent(editor, old);
    }
    if (component != null) {
      myFileEditorManager.addTopComponent(editor, component);
      editor.putUserData((Key<JComponent>)key, component);
    }
    else {
      editor.putUserData(key, null);
    }
  }
}
