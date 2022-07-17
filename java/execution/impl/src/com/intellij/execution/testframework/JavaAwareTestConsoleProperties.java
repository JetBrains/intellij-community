// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.testframework;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeSelectionModel;
import java.util.Collection;
import java.util.Iterator;

public abstract class JavaAwareTestConsoleProperties<T extends ModuleBasedConfiguration<JavaRunConfigurationModule, Element> & CommonJavaRunConfigurationParameters> extends SMTRunnerConsoleProperties {
  public JavaAwareTestConsoleProperties(final String testFrameworkName, RunConfiguration configuration, Executor executor) {
    super(configuration, testFrameworkName, executor);
    setPrintTestingStartedTime(false);
  }

  @Override
  public boolean isPaused() {
    final DebuggerSession debuggerSession = getDebugSession();
    return debuggerSession != null && debuggerSession.isPaused();
  }

  @Override
  public @NotNull T getConfiguration() {
    return (T)super.getConfiguration();
  }

  @Override
  public int getSelectionMode() {
    return TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
  }

  @Override
  public boolean fixEmptySuite() {
    return ResetConfigurationModuleAdapter.tryWithAnotherModule(getConfiguration(), isDebug());
  }

  @Nullable
  @Override
  public Navigatable getErrorNavigatable(@NotNull Location<?> location, @NotNull String stacktrace) {
    //navigate to the first stack trace
    return getStackTraceErrorNavigatable(location, stacktrace);
  }

  @Nullable
  public static Navigatable getStackTraceErrorNavigatable(@NotNull Location<?> location, @NotNull String stacktrace) {
    final PsiLocation<?> psiLocation = location.toPsiLocation();
    PsiClass containingClass = psiLocation.getParentElement(PsiClass.class);
    if (containingClass == null && location instanceof MethodLocation) {
      containingClass = ((MethodLocation)location).getContainingClass();
    }
    if (containingClass == null) return null;
    final String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName == null) return null;
    PsiMethod containingMethod = null;
    for (Iterator<Location<PsiMethod>> iterator = psiLocation.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final PsiMethod psiMethod = iterator.next().getPsiElement();
      if (containingClass.equals(psiMethod.getContainingClass())) containingMethod = psiMethod;
    }
    if (containingMethod == null) return null;
    String methodName = containingMethod.getName();
    StackTraceLine lastLine = null;
    final String[] stackTrace = new LineTokenizer(stacktrace).execute();
    for (String aStackTrace : stackTrace) {
      final StackTraceLine line = new StackTraceLine(containingClass.getProject(), aStackTrace);
      if (methodName.equals(line.getMethodName()) && qualifiedName.equals(line.getClassName())) {
        lastLine = line;
        break;
      }
    }
    if (lastLine != null) {
      try {
        int lineNumber = lastLine.getLineNumber();
        PsiFile psiFile = containingClass.getContainingFile();
        Document document = PsiDocumentManager.getInstance(containingClass.getProject()).getDocument(psiFile);
        TextRange textRange = containingMethod.getTextRange();
        if (textRange == null || document == null ||
            lineNumber >= 0 && lineNumber < document.getLineCount() && textRange.contains(document.getLineStartOffset(lineNumber))) {
          return new OpenFileDescriptor(containingClass.getProject(), psiFile.getVirtualFile(), lineNumber, 0);
        }
      }
      catch (NumberFormatException ignored) { }
    }
    return null;
  }

  @Nullable
  public DebuggerSession getDebugSession() {
    final DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(getProject());
    if (debuggerManager == null) return null;
    final Collection<DebuggerSession> sessions = debuggerManager.getSessions();
    for (final DebuggerSession debuggerSession : sessions) {
      if (getConsole() == debuggerSession.getProcess().getExecutionResult().getExecutionConsole()) return debuggerSession;
    }
    return null;
  }

  @Override
  public boolean isEditable() {
    return Registry.is("editable.java.test.console");
  }
}