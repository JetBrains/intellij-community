// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.ide.util.ModuleRendererFactory;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.TextWithIcon;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AlternativeSourceNotificationPanel extends EditorNotificationPanel {

  public AlternativeSourceNotificationPanel(@NotNull FileEditor fileEditor,
                                            @NotNull Project project,
                                            @NotNull @Nls String text,
                                            @NotNull VirtualFile file,
                                            AlternativeSourceElement[] alternatives,
                                            @Nullable String locationDeclName) {
    super(fileEditor, EditorNotificationPanel.Status.Info);

    setText(text);

    final ComboBox<AlternativeSourceElement> switcher = new ComboBox<>(alternatives);
    switcher.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        WriteIntentReadAction.run(() -> {
          final DebuggerSession session = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
          final PsiElement item = ((AlternativeSourceElement)switcher.getSelectedItem()).myElement;
          final VirtualFile vFile = item.getContainingFile().getVirtualFile();
          if (session != null && vFile != null) {
            session.getProcess().getManagerThread().schedule(PrioritizedTask.Priority.LOW, () -> {
              if (!StringUtil.isEmpty(locationDeclName)) {
                DebuggerUtilsEx.setAlternativeSourceUrl(locationDeclName, vFile.getUrl(), project);
              }
              DebuggerUIUtil.invokeLater(() -> {
                FileEditorManager.getInstance(project).closeFile(file);
                session.refresh(true);
              });
            });
          }
          else if (item instanceof Navigatable navigatable) {
            FileEditorManager.getInstance(project).closeFile(file);
            navigatable.navigate(true);
          }
        });
      }
    });
    myLinksPanel.add(switcher);
    createActionLabel(JavaDebuggerBundle.message("action.hide.text"), () -> {
      DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE = false;
      AlternativeSourceNotificationProvider.setFileProcessed(file, false);
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
      FileEditor editor = fileEditorManager.getSelectedEditor(file);
      if (editor != null) {
        fileEditorManager.removeTopComponent(editor, this);
      }
    });
  }

  public static class AlternativeSourceElement {
    private final PsiElement myElement;
    private final String myText;

    @RequiresBackgroundThread
    public AlternativeSourceElement(PsiElement element) {
      myElement = element;
      TextWithIcon moduleTextWithIcon = ModuleRendererFactory.findInstance(element).getModuleTextWithIcon(element);
      myText = moduleTextWithIcon == null ? "" : moduleTextWithIcon.getText();
    }

    public PsiElement getElement() {
      return myElement;
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}