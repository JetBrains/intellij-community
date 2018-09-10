// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.TestStateStorage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TestFailedLineManager implements FileEditorManagerListener {

  private final TestStateStorage myStorage;
  private final Map<VirtualFile, Map<String, TestInfo>> myMap;

  public static TestFailedLineManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TestFailedLineManager.class);
  }

  public TestFailedLineManager(Project project, TestStateStorage storage) {
    myStorage = storage;
    myMap =  FactoryMap.create(o -> new HashMap<>());
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  public TestInfo getTestInfo(@NotNull PsiMethod psiMethod) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class);
    if (psiClass == null) return null;
    TestFramework framework = TestFrameworks.detectFramework(psiClass);
    if (framework == null || !framework.isTestMethod(psiMethod)) return null;

    String url = "java:test://" + ClassUtil.getJVMClassName(psiClass) + "/" + psiMethod.getName();
    TestStateStorage.Record state = myStorage.getState(url);
    if (state == null) return new TestInfo(null);

    VirtualFile file = psiMethod.getContainingFile().getVirtualFile();
    Map<String, TestInfo> map = myMap.get(file);
    TestInfo info = map.get(url);
    if (info == null || !state.date.equals(info.myRecord.date)) {
      info = new TestInfo(state);
      map.put(url, info);
    }
    return info;
  }

  public TestStateStorage.Record getFailedLineState(PsiMethodCallExpression call) {
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
    if (psiMethod == null) return null;
    TestInfo info = getTestInfo(psiMethod);
    if (info == null || info.myRecord == null) return null;
    Document document = PsiDocumentManager.getInstance(call.getProject()).getDocument(call.getContainingFile());
    if (document == null) return null;
    if (info.myPointer != null) {
      PsiElement element = info.myPointer.getElement();
      if (element != null) {
        if (call == element) {
          info.myRecord.failedLine = document.getLineNumber(call.getTextOffset()) + 1;
          return info.myRecord;
        }
        return null;
      }
    }
    TestStateStorage.Record state = info.myRecord;
    if (state.failedLine == -1 || StringUtil.isEmpty(state.failedMethod)) return null;
    if (!state.failedMethod.equals(call.getMethodExpression().getText())) return null;
    if (state.failedLine != document.getLineNumber(call.getTextOffset()) + 1) return null;
    info.myPointer = SmartPointerManager.createPointer(call);
    return info.myRecord;
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    Map<String, TestInfo> map = myMap.remove(file);
    if (map != null) {
      map.forEach((s, info) -> myStorage.writeState(s, info.myRecord));
    }
  }

  public static class TestInfo {
    public TestStateStorage.Record myRecord;
    public SmartPsiElementPointer<PsiElement> myPointer;

    public TestInfo(TestStateStorage.Record record) {
      myRecord = record;
    }
  }
}
