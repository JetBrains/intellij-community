// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.HtmlChunk.raw;
import static com.intellij.openapi.util.text.HtmlChunk.tag;

/**
 * Context for ambiguous method call error
 * 
 * @param results all results of multiple resolve
 * @param methodCandidate1 first good candidate from results
 * @param methodCandidate2 second good candidate from results
 */
public record JavaAmbiguousCallContext(@NotNull JavaResolveResult @NotNull [] results,
                                       @NotNull MethodCandidateInfo methodCandidate1,
                                       @NotNull MethodCandidateInfo methodCandidate2) {
  @NotNull
  @Nls
  String description() {
    PsiMethod element1 = methodCandidate1.getElement();
    String m1 = PsiFormatUtil.formatMethod(element1,
                                           methodCandidate1.getSubstitutor(false),
                                           PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME |
                                           PsiFormatUtilBase.SHOW_PARAMETERS,
                                           PsiFormatUtilBase.SHOW_TYPE);
    PsiMethod element2 = methodCandidate2.getElement();
    String m2 = PsiFormatUtil.formatMethod(element2,
                                           methodCandidate2.getSubstitutor(false),
                                           PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME |
                                           PsiFormatUtilBase.SHOW_PARAMETERS,
                                           PsiFormatUtilBase.SHOW_TYPE);
    VirtualFile virtualFile1 = PsiUtilCore.getVirtualFile(element1);
    VirtualFile virtualFile2 = PsiUtilCore.getVirtualFile(element2);
    if (!Comparing.equal(virtualFile1, virtualFile2)) {
      if (virtualFile1 != null) m1 += " (In " + virtualFile1.getPresentableUrl() + ")";
      if (virtualFile2 != null) m2 += " (In " + virtualFile2.getPresentableUrl() + ")";
    }
    return JavaCompilationErrorBundle.message("call.ambiguous", m1, m2);
  }

  @NotNull HtmlChunk tooltip() {
    return raw(JavaCompilationErrorBundle.message("call.ambiguous.tooltip",
                                                  methodCandidate1.getElement().getParameterList().getParametersCount() + 2,
                                                  createAmbiguousMethodHtmlTooltipMethodRow(methodCandidate1),
                                                  getContainingClassName(methodCandidate1),
                                                  createAmbiguousMethodHtmlTooltipMethodRow(methodCandidate2),
                                                  getContainingClassName(methodCandidate2)));
  }

  private static @NotNull String getContainingClassName(@NotNull MethodCandidateInfo methodCandidate) {
    PsiMethod method = methodCandidate.getElement();
    PsiClass containingClass = method.getContainingClass();
    return containingClass == null ? method.getContainingFile().getName() : JavaErrorFormatUtil.formatClass(containingClass, false);
  }

  private static @NotNull HtmlChunk createAmbiguousMethodHtmlTooltipMethodRow(@NotNull MethodCandidateInfo methodCandidate) {
    PsiMethod method = methodCandidate.getElement();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
    HtmlBuilder row = new HtmlBuilder().append(tag("td").child(tag("b").addText(method.getName())));
    for (int j = 0; j < parameters.length; j++) {
      PsiParameter parameter = parameters[j];
      PsiType type = substitutor.substitute(parameter.getType());
      String typeText = (j == 0 ? "(" : "") + type.getPresentableText() + (j == parameters.length - 1 ? ")" : ",");
      row.append(tag("td").child(tag("b").addText(typeText)));
    }
    if (parameters.length == 0) {
      row.append(tag("td").child(tag("b").addText("()")));
    }
    return row.toFragment();
  }
}
