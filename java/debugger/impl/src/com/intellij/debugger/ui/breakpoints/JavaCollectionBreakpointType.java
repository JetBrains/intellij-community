// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.HelpID;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaCollectionBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaFieldBreakpointProperties;

import javax.swing.*;

@ApiStatus.Experimental
public final class JavaCollectionBreakpointType extends JavaLineBreakpointTypeBase<JavaCollectionBreakpointProperties> {

  public JavaCollectionBreakpointType() {
    super("java-collection", JavaDebuggerBundle.message("collection.watchpoints.tab.title"));
  }

  @Override
  public boolean isAddBreakpointButtonVisible() {
    return Registry.is("debugger.collection.watchpoints.enabled");
  }

  @NotNull
  @Override
  public Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getSuspendNoneIcon() {
    return AllIcons.Debugger.Db_no_suspend_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_muted_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_muted_disabled_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getInactiveDependentIcon() {
    return AllIcons.Debugger.Db_dep_field_breakpoint;
  }

  @Override
  public @Nullable XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaCollectionBreakpointProperties>> createCustomConditionsPanel() {
    return new CollectionBreakpointPropertiesPanel();
  }

  //@Override
  private static String getHelpID() {
    return HelpID.COLLECTION_WATCHPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return JavaDebuggerBundle.message("collection.watchpoints.tab.title");
  }


  @Nls
  public String getText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    JavaFieldBreakpointProperties properties = breakpoint.getProperties();
    final String className = properties.myClassName;
    return className != null && !className.isEmpty() ? className + "." + properties.myFieldName : properties.myFieldName;
  }

  @Nullable
  @Override
  public JavaCollectionBreakpointProperties createProperties() {
    return new JavaCollectionBreakpointProperties();
  }

  @Nullable
  @Override
  public JavaCollectionBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return new JavaCollectionBreakpointProperties();
  }

  @NotNull
  @Override
  public Breakpoint<JavaCollectionBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint breakpoint) {
    return new CollectionBreakpoint(project, breakpoint);
  }

  @Override
  public boolean canBeHitInOtherPlaces() {
    return true;
  }

  @Override
  public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
    if (!Registry.is("debugger.collection.watchpoints.enabled")) {
      return false;
    }
    return canPutAtElement(file, line, project, (element, document) -> {
      if (element instanceof PsiField) {
        boolean isFinal = ((PsiField)element).hasModifierProperty(PsiModifier.FINAL);
        boolean isPrivate = ((PsiField)element).hasModifierProperty(PsiModifier.PRIVATE);
        boolean isProtected = ((PsiField)element).hasModifierProperty(PsiModifier.PROTECTED);
        boolean hasValidModifiers = isFinal || isPrivate || isProtected;
        PsiType type = ((PsiField)element).getType();
        return hasValidModifiers && CollectionUtils.isCollectionClassOrInterface(type);
      }
      return false;
    });
  }

  @Nullable
  @Override
  public XLineBreakpoint<JavaCollectionBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final Ref<XLineBreakpoint<JavaCollectionBreakpointProperties>> result = Ref.create(null);
    AddFieldBreakpointDialog dialog = new AddFieldBreakpointDialog(project) {
      @Override
      protected boolean validateData() {
        final String className = getClassName();
        if (className.isEmpty()) {
          return false;
        }
        final String fieldName = getFieldName();
        if (fieldName.isEmpty()) {
          return false;
        }
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
        if (psiClass != null) {
          final PsiFile psiFile = psiClass.getContainingFile();
          Document document = psiFile.getViewProvider().getDocument();
          if (document != null) {
            PsiField field = psiClass.findFieldByName(fieldName, false);
            if (field != null) {
              final int line = document.getLineNumber(field.getTextOffset());
              WriteAction.run(() -> {
                XLineBreakpoint<JavaCollectionBreakpointProperties> fieldBreakpoint =
                  XDebuggerManager.getInstance(project).getBreakpointManager()
                    .addLineBreakpoint(JavaCollectionBreakpointType.this, psiFile.getVirtualFile().getUrl(), line,
                                       new JavaCollectionBreakpointProperties(fieldName, className));
                result.set(fieldBreakpoint);
              });
              return true;
            }
          }
        }
        return false;
      }
    };
    dialog.show();
    return result.get();
  }
}