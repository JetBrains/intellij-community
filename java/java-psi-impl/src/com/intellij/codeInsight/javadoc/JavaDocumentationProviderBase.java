package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaDocumentationProviderBase {
  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    if (element instanceof PsiMethodCallExpression) {
      return getMethodCandidateInfo((PsiMethodCallExpression)element);
    }


    //external documentation finder
    return generateExternalJavadoc(element);
  }

  @Nullable
  public static String generateExternalJavadoc(final PsiElement element) {
    final JavaDocInfoGenerator javaDocInfoGenerator = new JavaDocInfoGenerator(element.getProject(), element);
    final List<String> docURLs = getExternalJavaDocUrl(element);
    return JavaDocExternalFilter.filterInternalDocInfo(javaDocInfoGenerator.generateDocInfo(docURLs));
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
    sb.append("&nbsp;&nbsp;<a href=\"" + DocumentationManager.PSI_ELEMENT_PROTOCOL);
    sb.append(JavaDocUtil.getReferenceText(element.getProject(), element));
    sb.append("\">");
    sb.append(str);
    sb.append("</a>");
    sb.append("<br>");
  }
}
