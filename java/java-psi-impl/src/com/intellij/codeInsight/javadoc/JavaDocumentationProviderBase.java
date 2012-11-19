package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JavaDocumentationProviderBase {
  @NonNls public static final String HTML_EXTENSION = ".html";
  @NonNls public static final String PACKAGE_SUMMARY_FILE = "package-summary.html";

  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    if (element instanceof PsiMethodCallExpression) {
      return getMethodCandidateInfo((PsiMethodCallExpression)element);
    }

    //external documentation finder
    return generateExternalJavadoc(element);
  }

  @Nullable
  public List<String> getExternalJavaDocUrl(final PsiElement element) {
    List<String> urls = null;

    if (element instanceof PsiClass) {
      urls = findUrlForClass((PsiClass)element);
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        urls = findUrlForClass(aClass);
        if (urls != null) {
          for (int i = 0; i < urls.size(); i++) {
            urls.set(i, urls.get(i) + "#" + field.getName());
          }
        }
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        final List<String> classUrls = findUrlForClass(aClass);

        if (classUrls != null) {
          urls = new ArrayList<String>();
          String signature = formatMethodSignature(method);
          for (String classUrl : classUrls) {
            urls.add(classUrl + "#" + signature);
          }
          signature = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                 PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                 PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES, 999);
          for (String classUrl : classUrls) {
            urls.add(classUrl + "#" + signature);
          }
        }
      }
    }
    else if (element instanceof PsiPackage) {
      urls = findUrlForPackage((PsiPackage)element);
    }
    else if (element instanceof PsiDirectory) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
      if (aPackage != null) {
        urls = findUrlForPackage(aPackage);
      }
    }

    if (urls == null) {
      return null;
    }
    else {
      for (int i = 0; i < urls.size(); i++) {
        urls.set(i, FileUtil.toSystemIndependentName(urls.get(i)));
      }
      return urls;
    }
  }

  @Nullable
  public List<String> findUrlForClass(PsiClass aClass) {
    String qName = aClass.getQualifiedName();
    if (qName == null) return null;
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    String packageName = ((PsiJavaFile)file).getPackageName();

    String relPath;
    if (packageName.length() > 0) {
      relPath = packageName.replace('.', '/') + '/' + qName.substring(packageName.length() + 1) + HTML_EXTENSION;
    }
    else {
      relPath = qName + HTML_EXTENSION;
    }

    final PsiFile containingFile = aClass.getContainingFile();
    if (containingFile == null) return null;
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;

    return findUrlForVirtualFile(containingFile.getProject(), virtualFile, relPath);
  }

  @Nullable
  public List<String> findUrlForVirtualFile(final Project project, final VirtualFile virtualFile, final String relPath) {
    return null;
  }

  @Nullable
  public List<String> findUrlForPackage(PsiPackage aPackage) {
    String qName = aPackage.getQualifiedName();
    qName = qName.replace('.', '/') + '/' + PACKAGE_SUMMARY_FILE;
    for (PsiDirectory directory : aPackage.getDirectories()) {
      List<String> url = findUrlForVirtualFile(aPackage.getProject(), directory.getVirtualFile(), qName);
      if (url != null) {
        return url;
      }
    }
    return null;
  }

  @Nullable
  public String generateExternalJavadoc(final PsiElement element) {
    final JavaDocInfoGenerator javaDocInfoGenerator = new JavaDocInfoGenerator(element.getProject(), element);
    final List<String> docURLs = getExternalJavaDocUrl(element);
    return javaDocInfoGenerator.generateDocInfo(docURLs);
  }

  private String getMethodCandidateInfo(PsiMethodCallExpression expr) {
    final PsiResolveHelper rh = JavaPsiFacade.getInstance(expr.getProject()).getResolveHelper();
    final CandidateInfo[] candidates = rh.getReferencedMethodCandidates(expr, true);
    final String text = expr.getText();
    if (candidates.length > 0) {
      @NonNls final StringBuilder sb = new StringBuilder();

      for (final CandidateInfo candidate : candidates) {
        final PsiElement element = candidate.getElement();

        if (!(element instanceof PsiMethod)) {
          continue;
        }

        final String str = PsiFormatUtil.formatMethod((PsiMethod)element, candidate.getSubstitutor(), PsiFormatUtilBase.SHOW_NAME |
                                                                                                      PsiFormatUtilBase.SHOW_TYPE |
                                                                                                      PsiFormatUtilBase.SHOW_PARAMETERS,
                                                      PsiFormatUtilBase.SHOW_TYPE);
        createElementLink(sb, element, StringUtil.escapeXml(str));
      }

      return CodeInsightBundle.message("javadoc.candiates", text, sb);
    }

    return CodeInsightBundle.message("javadoc.candidates.not.found", text);
  }

  private static void createElementLink(@NonNls final StringBuilder sb, final PsiElement element, final String str) {
    sb.append("&nbsp;&nbsp;");
    DocumentationManagerUtil.createHyperlink(sb, element, JavaDocUtil.getReferenceText(element.getProject(), element), str, true);
    sb.append("<br>");
  }

  public static String formatMethodSignature(PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                      PsiFormatUtilBase.SHOW_NAME |
                                      PsiFormatUtilBase.SHOW_PARAMETERS |
                                      PsiFormatUtilBase.SHOW_RAW_NON_TOP_TYPE,
                                      PsiFormatUtilBase.SHOW_TYPE |
                                      PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES |
                                      PsiFormatUtilBase.SHOW_RAW_NON_TOP_TYPE,
                                      999);
  }

}
