// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.java.codeserver.highlighting.JavaErrorFilter;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorHighlightType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.NewUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.*;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Java highlighting: reports compilation errors in Java code.
 * Internal class; do not use directly. 
 * If you need to check whether a block of code contains Java errors, use {@link JavaErrorCollector}.
 * If you want to filter out some error messages, implement {@link JavaErrorFilter} extension.
 */
@ApiStatus.Internal
public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
  private Map<String, String> myTooltipStyles;
  private JavaErrorCollector myCollector;

  protected HighlightVisitorImpl() {
  }

  private static @NotNull Map<String, String> initTooltipStyles() {
    Color parameterBgColor = EditorColorsUtil.getGlobalOrDefaultColorScheme()
      .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT).getBackgroundColor();
    String parameterBgStyle = parameterBgColor == null ? "" : "; background-color: " + ColorUtil.toHtmlColor(parameterBgColor);
    return Map.of(
      JavaCompilationError.JAVA_DISPLAY_INFORMATION,
      "color: " + ColorUtil.toHtmlColor(NewUI.isEnabled() ? JBUI.CurrentTheme.Editor.Tooltip.FOREGROUND : UIUtil.getToolTipForeground()),
      JavaCompilationError.JAVA_DISPLAY_GRAYED,
      "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()),
      JavaCompilationError.JAVA_DISPLAY_PARAMETER,
      "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()) + parameterBgStyle,
      JavaCompilationError.JAVA_DISPLAY_ERROR,
      "color: " + ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground()));
  }

  /**
   * @deprecated use {@link #HighlightVisitorImpl()}
   */
  @Deprecated(forRemoval = true)
  protected HighlightVisitorImpl(@NotNull PsiResolveHelper psiResolveHelper) {
  }

  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public @NotNull HighlightVisitorImpl clone() {
    return new HighlightVisitorImpl();
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile psiFile) {
    HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(psiFile.getProject());
    if (highlightingLevelManager.runEssentialHighlightingOnly(psiFile)) {
      return false;
    }

    // both PsiJavaFile and PsiCodeFragment must match
    return psiFile instanceof PsiImportHolder && !InjectedLanguageManager.getInstance(psiFile.getProject()).isInjectedFragment(psiFile);
  }

  @Override
  public boolean supersedesDefaultHighlighter() {
    return true;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    element.accept(this);
  }

  @Override
  public boolean analyze(@NotNull PsiFile psiFile, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable highlight) {
    try {
      prepare(holder, psiFile);
      if (updateWholeFile) {
        GlobalInspectionContextBase.assertUnderDaemonProgress();
        Project project = psiFile.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        highlight.run();
        ProgressManager.checkCanceled();
        if (document != null) {
          new UnusedImportsVisitor(psiFile, document).collectHighlights(holder);
        }
      }
      else {
        highlight.run();
      }
    }
    finally {
      myCollector = null;
    }

    return true;
  }

  private void prepare(@NotNull HighlightInfoHolder holder, @NotNull PsiFile psiFile) {
    myCollector = new JavaErrorCollector(psiFile, error -> reportError(error, holder));
  }

  private void reportError(@NotNull JavaCompilationError<?, ?> error, @NotNull HighlightInfoHolder holder) {
    if (error.psiForKind(SYNTAX_ERROR)
      .filter(e -> HighlightErrorFilter.EP_NAME.findFirstSafe(e.getProject(), filter -> !filter.shouldHighlightErrorElement(e)) != null)
      .isPresent()) {
      return;
    }
    JavaErrorHighlightType javaHighlightType = error.highlightType();
    HighlightInfoType type = switch (javaHighlightType) {
      case ERROR, FILE_LEVEL_ERROR -> HighlightInfoType.ERROR;
      case UNHANDLED_EXCEPTION -> HighlightInfoType.UNHANDLED_EXCEPTION;
      case WRONG_REF -> HighlightInfoType.WRONG_REF;
      case PENDING_REF -> HighlightInfoType.PENDING_REFERENCE;
    };
    HtmlChunk tooltip = error.tooltip();
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(type);
    if (tooltip.isEmpty()) {
      info.descriptionAndTooltip(error.description());
    } else {
      if (myTooltipStyles == null) {
        myTooltipStyles = initTooltipStyles();
      }
      info.description(error.description()).escapedToolTip(
        tooltip.applyStyles(myTooltipStyles).toString());
    }
    if (javaHighlightType == JavaErrorHighlightType.FILE_LEVEL_ERROR) {
      info.fileLevelAnnotation();
    }
    TextRange range = error.range();
    info.range(range);
    if (range.getLength() == 0) {
      int offset = range.getStartOffset();
      CharSequence sequence = holder.getContextFile().getFileDocument().getCharsSequence();
      if (offset >= sequence.length() || sequence.charAt(offset) == '\n') {
        info.endOfLine();
      }
    }
    Consumer<@NotNull CommonIntentionAction> consumer = fix -> info.registerFix(fix.asIntention(), null, null, null, null);
    JavaErrorFixProvider.EP_NAME.forEachExtensionSafe(provider -> provider.registerFixes(error, consumer));
    error.psiForKind(EXPRESSION_EXPECTED, REFERENCE_UNRESOLVED, REFERENCE_AMBIGUOUS)
      .or(() -> error.psiForKind(ACCESS_PRIVATE, ACCESS_PACKAGE_LOCAL, ACCESS_PROTECTED).map(psi -> tryCast(psi, PsiJavaCodeReferenceElement.class)))
      .or(() -> error.psiForKind(TYPE_UNKNOWN_CLASS).map(PsiTypeElement::getInnermostComponentReferenceElement))
      .or(() -> error.psiForKind(CALL_AMBIGUOUS_NO_MATCH, CALL_UNRESOLVED).map(PsiMethodCallExpression::getMethodExpression))
      .ifPresent(ref -> UnresolvedReferenceQuickFixProvider.registerUnresolvedReferenceLazyQuickFixes(ref, info));
    holder.add(info.create());
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    myCollector.processElement(element);
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    // Necessary to call visitElement, as super-implementation is empty
    visitElement(expression);
  }
}
