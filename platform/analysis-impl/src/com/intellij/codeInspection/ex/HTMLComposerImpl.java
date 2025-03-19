// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.lang.HTMLComposerExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.reference.*;
import com.intellij.lang.Language;
import com.intellij.openapi.project.ProjectUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class HTMLComposerImpl extends HTMLComposer {
  private final Map<Key<? extends HTMLComposerExtension<?>>, HTMLComposerExtension<?>> myExtensions = new HashMap<>();
  private final Map<Language, HTMLComposerExtension<?>> myLanguageExtensions = new HashMap<>();
  protected static final @NonNls String BR = "<br>";
  public static final @NonNls String NBSP = "&nbsp;";
  public static final @NonNls String CODE_CLOSING = "</code>";
  public static final @NonNls String CODE_OPENING = "<code>";
  public static final @NonNls String B_OPENING = "<b>";
  public static final @NonNls String B_CLOSING = "</b>";

  protected static final @NonNls String CLOSE_TAG = "\">";
  public static final @NonNls String A_HREF_OPENING = "<a href=\"";
  public static final @NonNls String A_CLOSING = "</a>";

  protected HTMLComposerImpl() {
    for (InspectionExtensionsFactory factory : InspectionExtensionsFactory.EP_NAME.getExtensionList()) {
      final HTMLComposerExtension<?> extension = factory.createHTMLComposerExtension(this);
      if (extension != null) {
        myExtensions.put(extension.getID(), extension);
        extension.getLanguages().forEach(l -> myLanguageExtensions.put(l, extension));
      }
    }
  }

  public abstract void compose(@NotNull StringBuilder buf, RefEntity refEntity);

  public void compose(@NotNull StringBuilder buf, RefEntity refElement, CommonProblemDescriptor descriptor) {}

  protected void genPageHeader(@NotNull StringBuilder buf, RefEntity refEntity) {
    if (refEntity instanceof RefElement refElement) {

      appendHeading(buf, AnalysisBundle.message("inspection.export.results.capitalized.location"));
      buf.append("<div class=\"location\">");
      appendShortName(buf, refElement);
      buf.append(BR);
      buf.append("in ");
      appendLocation(buf, refElement);
      buf.append("</div>");
    }
  }

  private void appendLocation(@NotNull StringBuilder buf, final RefElement refElement) {
    final HTMLComposerExtension<?> extension = getLanguageExtension(refElement);
    if (extension != null) {
      extension.appendLocation(refElement, buf);
    }
    if (refElement instanceof RefFile){
      buf.append(AnalysisBundle.message("inspection.export.results.file"));
      buf.append(NBSP);
      appendElementReference(buf, refElement, false);
    }
    else if (refElement instanceof RefDirectory){
      buf.append(AnalysisBundle.message("inspection.export.results.directory"));
      buf.append(NBSP);
      appendElementReference(buf, refElement, false);
    }
  }

  private @Nullable HTMLComposerExtension<?> getLanguageExtension(final RefElement refElement) {
    final PsiElement element = refElement.getPsiElement();
    return element != null ? myLanguageExtensions.get(element.getLanguage()) : null;
  }

  private void appendShortName(@NotNull StringBuilder buf, RefElement refElement) {
    final HTMLComposerExtension<?> extension = getLanguageExtension(refElement);
    if (extension != null) {
      extension.appendShortName(refElement, buf);
    } else {
      refElement.accept(new RefVisitor() {
        @Override public void visitFile(@NotNull RefFile file) {
          final PsiFile psiFile = file.getPsiElement();
          if (psiFile != null) {
            buf.append(B_OPENING);
            buf.append(psiFile.getName());
            buf.append(B_CLOSING);
          }
        }
      });
    }
  }

  @Override
  public void appendElementReference(@NotNull StringBuilder buf, RefElement refElement) {
    appendElementReference(buf, refElement, true);
  }

  @Override
  public void appendElementReference(@NotNull StringBuilder buf, RefElement refElement, String linkText, @NonNls String frameName) {
    final String url = ((RefElementImpl)refElement).getURL();
    if (url != null) {
      appendElementReference(buf, url, linkText, frameName);
    }
  }

  @Override
  public void appendElementReference(@NotNull StringBuilder buf, String url, String linkText, @NonNls String frameName) {
    buf.append(A_HREF_OPENING);
    buf.append(url);
    if (frameName != null) {
      final @NonNls String target = "\" target=\"";
      buf.append(target);
      buf.append(frameName);
    }

    buf.append("\">");
    buf.append(linkText);
    buf.append(A_CLOSING);
  }

  @SuppressWarnings("MethodMayBeStatic")
  protected void appendQuickFix(@NotNull StringBuilder buf, String text) {
    buf.append(text);
  }

  @Override
  public void appendElementReference(@NotNull StringBuilder buf, RefElement refElement, boolean isPackageIncluded) {
    final HTMLComposerExtension<?> extension = getLanguageExtension(refElement);

    if (extension != null) {
      extension.appendReferencePresentation(refElement, buf, isPackageIncluded);
    } else if (refElement instanceof RefFile || refElement instanceof RefDirectory) {
      buf.append(A_HREF_OPENING).append(((RefElementImpl)refElement).getURL()).append("\">");

      String refElementName = refElement.getName();
      final PsiElement element = refElement.getPsiElement();
      if (element != null) {
        VirtualFile file = PsiUtilCore.getVirtualFile(element);
        if (file != null) {
          refElementName = ProjectUtilCore.displayUrlRelativeToProject(file, file.getPresentableUrl(), element.getProject(), true, false);
        }
      }
      buf.append(refElementName);
      buf.append(A_CLOSING);
    }
  }

  @Override
  public String composeNumereables(int n, String statement, String singleEnding, String multipleEnding) {
    final StringBuilder buf = new StringBuilder();
    buf.append(n).append(' ').append(statement);

    if (n % 10 == 1 && n % 100 != 11) {
      buf.append(singleEnding);
    }
    else {
      buf.append(multipleEnding);
    }
    return buf.toString();
  }

  @Override
  public void appendElementInReferences(@NotNull StringBuilder buf, RefElement refElement) {
    appendSection(buf, AnalysisBundle.message("inspection.export.results.used.from"), refElement.getInReferences());
  }

  @Override
  public void appendElementOutReferences(@NotNull StringBuilder buf, RefElement refElement) {
    appendSection(buf, AnalysisBundle.message("inspection.export.results.uses"), refElement.getOutReferences());
  }

  @Override
  public void appendListItem(@NotNull StringBuilder buf, RefElement refElement) {
    startListItem(buf);
    appendElementReference(buf, refElement, true);
    appendAdditionalListItemInfo(buf, refElement);
    doneListItem(buf);
  }

  protected void appendAdditionalListItemInfo(@NotNull StringBuilder buf, RefElement refElement) {
    // Default appends nothing.
  }

  protected void appendResolution(@NotNull StringBuilder buf, RefEntity where, String[] quickFixes) {
    if (where instanceof RefElement && !where.isValid()) return;
    if (quickFixes != null) {
      boolean listStarted = false;
      for (final String text : quickFixes) {
        if (text == null) continue;
        if (!listStarted) {
          appendHeading(buf, AnalysisBundle.message("inspection.problem.resolution"));
          startList(buf);
          listStarted = true;
        }
        startListItem(buf);
        appendQuickFix(buf, text);
        doneListItem(buf);
      }

      if (listStarted) {
        doneList(buf);
      }
    }
  }

  @Override
  public void startList(@NotNull StringBuilder buf) {
    buf.append("\n<ul>");
  }

  @Override
  public void doneList(@NotNull StringBuilder buf) {
    buf.append("\n</ul>");
  }

  @Override
  public void startListItem(@NotNull StringBuilder buf) {
    buf.append("\n<li>");
  }

  public static void doneListItem(@NotNull StringBuilder buf) {}

  @Override
  public void startProblemDescription(@NotNull StringBuilder buf) {
    buf.append("\n<div class=\"problem-description\">");
  }

  @Override
  public void doneProblemDescription(@NotNull StringBuilder buf) {
    buf.append("\n</div>");
  }

  @Override
  public void appendNoProblems(@NotNull StringBuilder buf) {
    buf.append("<p class=\"problem-description-group\">");
    buf.append(AnalysisBundle.message("inspection.export.results.no.problems.found"));
    buf.append("</p>");
  }

  @Override
  public <T> T getExtension(Key<T> key) {
    //noinspection unchecked
    return (T)myExtensions.get(key);
  }
}
