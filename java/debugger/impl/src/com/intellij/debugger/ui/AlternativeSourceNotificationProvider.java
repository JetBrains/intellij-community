// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.ide.util.ModuleRendererFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.TextWithIcon;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public final class AlternativeSourceNotificationProvider implements EditorNotificationProvider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("AlternativeSource");
  private static final Key<Boolean> FILE_PROCESSED_KEY = Key.create("AlternativeSourceCheckDone");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public @NotNull ComponentProvider<EditorNotificationPanel> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    if (!DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE) {
      return ComponentProvider.getDummy();
    }

    DebuggerSession javaSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
    XDebugSession session = javaSession != null ? javaSession.getXDebugSession() : null;

    if (session == null) {
      setFileProcessed(file, false);
      return ComponentProvider.getDummy();
    }

    XSourcePosition position = session.getCurrentPosition();
    if (position == null || !file.equals(position.getFile())) {
      setFileProcessed(file, false);
      return ComponentProvider.getDummy();
    }

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return ComponentProvider.getDummy();

    if (!(psiFile instanceof PsiJavaFile)) return ComponentProvider.getDummy();

    PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
    if (classes.length == 0) return ComponentProvider.getDummy();

    PsiClass baseClass = classes[0];
    String name = baseClass.getQualifiedName();

    if (name == null) return ComponentProvider.getDummy();

    if (DumbService.getInstance(project).isDumb()) return ComponentProvider.getDummy();

    PsiClass[] altClasses = JavaPsiFacade.getInstance(project).findClasses(name, javaSession.getSearchScope());
    if (altClasses.length == 0) {
      altClasses = JavaPsiFacade.getInstance(project).findClasses(name, GlobalSearchScope.allScope(project));
    }
    ArrayList<PsiClass> alts = ContainerUtil.newArrayList(altClasses);
    ContainerUtil.removeDuplicates(alts);

    setFileProcessed(file, true);

    if (alts.size() > 1) {
      for (PsiClass cls : alts) {
        if (cls.equals(baseClass) || cls.getNavigationElement().equals(baseClass)) {
          alts.remove(cls);
          break;
        }
      }
      alts.add(0, baseClass);

      ComboBoxClassElement[] elems = ContainerUtil.map2Array(alts,
                                                             ComboBoxClassElement.class,
                                                             psiClass -> new ComboBoxClassElement((PsiClass)psiClass.getNavigationElement()));

      String locationDeclName = null;
      XStackFrame frame = session.getCurrentStackFrame();
      if (frame instanceof JavaStackFrame) {
        Location location = ((JavaStackFrame)frame).getDescriptor().getLocation();
        if (location != null) {
          locationDeclName = location.declaringType().name();
        }
      }

      String finalLocationDeclName = locationDeclName;
      return fileEditor -> new AlternativeSourceNotificationPanel(elems, baseClass, project, file, fileEditor, finalLocationDeclName);
    }
    return ComponentProvider.getDummy();
  }

  private static class ComboBoxClassElement {
    private final PsiClass myClass;
    private String myText;

    ComboBoxClassElement(PsiClass aClass) {
      myClass = aClass;
    }

    @Override
    public String toString() {
      if (myText == null) {
        ModuleRendererFactory factory = ModuleRendererFactory.findInstance(myClass);
        TextWithIcon moduleTextWithIcon = factory.getModuleTextWithIcon(myClass);
        myText = moduleTextWithIcon == null ? "" : moduleTextWithIcon.getText();
      }
      return myText;
    }
  }

  public static boolean isFileProcessed(VirtualFile file) {
    return FILE_PROCESSED_KEY.get(file) != null;
  }

  public static void setFileProcessed(VirtualFile file, boolean value) {
    FILE_PROCESSED_KEY.set(file, value ? Boolean.TRUE : null);
  }

  private static class AlternativeSourceNotificationPanel extends EditorNotificationPanel {
    AlternativeSourceNotificationPanel(ComboBoxClassElement[] alternatives,
                                              final PsiClass aClass,
                                              @NotNull Project project,
                                              @NotNull VirtualFile file,
                                              @NotNull FileEditor fileEditor,
                                              String locationDeclName) {
      super(fileEditor);

      setText(JavaDebuggerBundle.message("editor.notification.alternative.source", aClass.getQualifiedName()));
      final ComboBox<ComboBoxClassElement> switcher = new ComboBox<>(alternatives);
      switcher.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(project).getContext();
          final DebuggerSession session = context.getDebuggerSession();
          final PsiClass item = ((ComboBoxClassElement)switcher.getSelectedItem()).myClass;
          final VirtualFile vFile = item.getContainingFile().getVirtualFile();
          if (session != null && vFile != null) {
            session.getProcess().getManagerThread().schedule(new DebuggerCommandImpl() {
              @Override
              protected void action() {
                if (!StringUtil.isEmpty(locationDeclName)) {
                  DebuggerUtilsEx.setAlternativeSourceUrl(locationDeclName, vFile.getUrl(), project);
                }
                DebuggerUIUtil.invokeLater(() -> {
                  FileEditorManager.getInstance(project).closeFile(file);
                  session.refresh(true);
                });
              }
            });
          }
          else {
            FileEditorManager.getInstance(project).closeFile(file);
            item.navigate(true);
          }
        }
      });
      myLinksPanel.add(switcher);
      createActionLabel(JavaDebuggerBundle.message("action.hide.text"), () -> {
        DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE = false;
        setFileProcessed(file, false);
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        FileEditor editor = fileEditorManager.getSelectedEditor(file);
        if (editor != null) {
          fileEditorManager.removeTopComponent(editor, this);
        }
      });
    }
  }
}
