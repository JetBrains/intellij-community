// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.AlternativeSourceNotificationPanel.AlternativeSourceElement;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class AlternativeSourceNotificationProvider implements EditorNotificationProvider {

  private static final Key<Boolean> FILE_PROCESSED_KEY = Key.create("AlternativeSourceCheckDone");

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    if (!DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE) {
      return null;
    }

    if (DumbService.getInstance(project).isDumb()) {
      return null;
    }

    DebuggerSession javaSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
    XDebugSession session = javaSession != null ? javaSession.getXDebugSession() : null;

    if (session == null) {
      setFileProcessed(file, false);
      return null;
    }

    XSourcePosition position = session.getCurrentPosition();
    if (position == null || !file.equals(position.getFile())) {
      setFileProcessed(file, false);
      return null;
    }

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof PsiJavaFile)) {
      return null;
    }

    PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
    if (classes.length == 0) {
      return null;
    }

    PsiClass baseClass = classes[0];
    String name = baseClass.getQualifiedName();
    if (name == null) {
      return null;
    }

    PsiClass[] altClasses = JavaPsiFacade.getInstance(project).findClasses(name, javaSession.getSearchScope());
    if (altClasses.length == 0) {
      altClasses = JavaPsiFacade.getInstance(project).findClasses(name, GlobalSearchScope.allScope(project));
    }
    setFileProcessed(file, true);
    Set<PsiClass> uniqClasses = ContainerUtil.newHashSet(altClasses);
    if (uniqClasses.size() <= 1) {
      return null;
    }
    List<PsiClass> otherClasses = ContainerUtil.filter(uniqClasses,
      cls -> !(cls.equals(baseClass) || cls.getNavigationElement().equals(baseClass)));
    List<PsiClass> allClasses = ContainerUtil.prepend(otherClasses, baseClass);
    AlternativeSourceElement[] elems = ContainerUtil.map2Array(allClasses,
                                                               AlternativeSourceElement.class,
                                                               psiClass -> new AlternativeSourceElement(psiClass.getNavigationElement()));

    String locationDeclName = null;
    XStackFrame frame = session.getCurrentStackFrame();
    if (frame instanceof JavaStackFrame) {
      Location location = ((JavaStackFrame)frame).getDescriptor().getLocation();
      if (location != null) {
        locationDeclName = location.declaringType().name();
      }
    }

    String finalLocationDeclName = locationDeclName;
    return fileEditor -> new AlternativeSourceNotificationPanel(fileEditor,
                                                                project,
                                                                JavaDebuggerBundle.message("editor.notification.alternative.source", name),
                                                                file,
                                                                elems,
                                                                finalLocationDeclName
    );
  }

  public static boolean isFileProcessed(VirtualFile file) {
    return FILE_PROCESSED_KEY.get(file) != null;
  }

  public static void setFileProcessed(VirtualFile file, boolean value) {
    FILE_PROCESSED_KEY.set(file, value ? Boolean.TRUE : null);
  }
}
