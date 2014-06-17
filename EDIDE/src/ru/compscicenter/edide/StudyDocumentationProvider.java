package ru.compscicenter.edide;

import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * User: lia
 * Date: 24.05.14
 * Time: 22:13
 */
class StudyDocumentationProvider extends DocumentationProviderEx {

  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return "Study docs";
  }

  @Nullable
  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }


  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    String file = element.getContainingFile().getName();
    Editor editor = StudyEditor.getRecentOpenedEditor(element.getProject());
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    TaskManager tm = TaskManager.getInstance();
    int taskNum = tm.getTaskNumForFile(file);
    String docsFile = tm.getDocFileForTask(taskNum, pos, file);
    if (docsFile == null) {
      docsFile = "empty_study.docs";
    }
    InputStream ip = StudyDocumentationProvider.class.getResourceAsStream(docsFile);
    BufferedReader bf = new BufferedReader(new InputStreamReader(ip));
    StringBuilder text = new StringBuilder();
    try {
      while (bf.ready()) {
        String line = bf.readLine();
        text.append(line).append("\n");
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return text.toString();
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }

  @Override
  public PsiElement getCustomDocumentationElement(@NotNull final Editor editor,
                                                  @NotNull final PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    return null;
  }
}
