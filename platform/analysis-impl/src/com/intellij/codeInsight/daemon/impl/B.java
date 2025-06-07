// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
final class B implements AnnotationBuilder {
  private final @NotNull AnnotationHolderImpl myHolder;
  private final @Nls String message;
  private final @NotNull PsiElement myCurrentElement;
  private final @NotNull Object myCurrentAnnotator;
  private final @NotNull HighlightSeverity severity;
  private TextRange range;
  private Boolean afterEndOfLine;
  private Boolean fileLevel;
  private GutterIconRenderer gutterIconRenderer;
  private ProblemGroup problemGroup;
  private TextAttributes enforcedAttributes;
  private TextAttributesKey textAttributesKey;
  private ProblemHighlightType highlightType;
  private Boolean needsUpdateOnTyping;
  private @NlsContexts.Tooltip String tooltip;
  private List<FixB> fixes;
  private boolean created;
  private final Throwable myDebugCreationPlace;
  private List<@NotNull Consumer<? super QuickFixActionRegistrar>> myLazyQuickFixes;

  B(@NotNull AnnotationHolderImpl holder,
    @NotNull HighlightSeverity severity,
    @Nls @Nullable String message,
    @NotNull PsiElement currentElement,
    @NotNull Object currentAnnotator) {
    myHolder = holder;
    this.severity = severity;
    this.message = message;
    myCurrentElement = currentElement;
    myCurrentAnnotator = currentAnnotator;
    holder.annotationBuilderCreated(this);

    Application app = ApplicationManager.getApplication();
    myDebugCreationPlace = app.isUnitTestMode() && !ApplicationManagerEx.isInStressTest() || app.isInternal() ?
                           ThrowableInterner.intern(new Throwable()) : null;
  }

  private void assertNotSet(Object o, String description) {
    if (o != null) {
      markNotAbandoned(); // it crashed, not abandoned
      throw new IllegalStateException(description + " was set already");
    }
  }

  private void markNotAbandoned() {
    created = true;
  }

  private class FixB implements FixBuilder {
    @NotNull
    final CommonIntentionAction fix;
    TextRange range;
    HighlightDisplayKey key;
    Boolean batch;
    Boolean universal;

    FixB(@NotNull CommonIntentionAction fix) {
      this.fix = fix;
    }

    @Override
    public @NotNull FixBuilder range(@NotNull TextRange range) {
      assertNotSet(this.range, "range");
      this.range = range;
      return this;
    }

    @Override
    public @NotNull FixBuilder key(@NotNull HighlightDisplayKey key) {
      assertNotSet(this.key, "key");
      this.key = key;
      return this;
    }

    @Override
    public @NotNull FixBuilder batch() {
      assertNotSet(this.universal, "universal");
      assertNotSet(this.batch, "batch");
      assertLQF();
      this.batch = true;
      return this;
    }

    private void assertLQF() {
      if (!(fix instanceof LocalQuickFix ||
            fix instanceof ModCommandAction ||
            fix instanceof QuickFixWrapper ||
            fix instanceof LocalQuickFixAsIntentionAdapter)) {
        markNotAbandoned();
        throw new IllegalArgumentException(
          "Fix " + fix + " must be instance of LocalQuickFix or ModCommandAction to be registered as batch");
      }
    }

    @Override
    public @NotNull FixBuilder universal() {
      assertNotSet(this.universal, "universal");
      assertNotSet(this.batch, "batch");
      assertLQF();
      this.universal = true;
      return this;
    }

    @Override
    public @NotNull AnnotationBuilder registerFix() {
      if (fixes == null) {
        fixes = new SmartList<>();
      }
      fixes.add(this);
      return B.this;
    }

    @Override
    public String toString() {
      return fix + (range == null ? "" : " at " + range) + (batch == null ? "" : " batch") + (universal == null ? "" : " universal");
    }
  }

  @Override
  public @NotNull AnnotationBuilder withFix(@NotNull IntentionAction fix) {
    return newFix(fix).registerFix();
  }

  @Override
  public @NotNull AnnotationBuilder withFix(@NotNull CommonIntentionAction fix) {
    return newFix(fix).registerFix();
  }

  @Override
  public @NotNull FixBuilder newFix(@NotNull IntentionAction fix) {
    return new FixB(fix);
  }

  @Override
  public @NotNull FixBuilder newFix(@NotNull CommonIntentionAction fix) {
    return new FixB(fix);
  }

  @Override
  public @NotNull FixBuilder newLocalQuickFix(@NotNull LocalQuickFix fix, @NotNull ProblemDescriptor problemDescriptor) {
    return new FixB(QuickFixWrapper.wrap(problemDescriptor, fix));
  }

  @Override
  public @NotNull AnnotationBuilder withLazyQuickFix(@NotNull Consumer<? super QuickFixActionRegistrar> quickFixComputer) {
    if (myLazyQuickFixes == null) {
      myLazyQuickFixes = new SmartList<>();
    }
    myLazyQuickFixes.add(quickFixComputer);
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder range(@NotNull TextRange range) {
    assertNotSet(this.range, "range");
    TextRange currentElementRange = myCurrentElement.getTextRange();
    if (!currentElementRange.contains(range)) {
      markNotAbandoned();
      String message = "Range must be inside element being annotated: " + currentElementRange + "; but got: " + range;
      throw PluginException.createByClass(message, null, myCurrentAnnotator.getClass());
    }

    this.range = range;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder range(@NotNull ASTNode element) {
    return range(element.getTextRange());
  }

  @Override
  public @NotNull AnnotationBuilder range(@NotNull PsiElement element) {
    return range(element.getTextRange());
  }

  @Override
  public @NotNull AnnotationBuilder afterEndOfLine() {
    assertNotSet(afterEndOfLine, "afterEndOfLine");
    afterEndOfLine = true;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder fileLevel() {
    assertNotSet(fileLevel, "fileLevel");
    fileLevel = true;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer) {
    assertNotSet(this.gutterIconRenderer, "gutterIconRenderer");
    this.gutterIconRenderer = gutterIconRenderer;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder problemGroup(@NotNull ProblemGroup problemGroup) {
    assertNotSet(this.problemGroup, "problemGroup");
    this.problemGroup = problemGroup;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder enforcedTextAttributes(@NotNull TextAttributes enforcedAttributes) {
    assertNotSet(this.enforcedAttributes, "enforcedAttributes");
    this.enforcedAttributes = enforcedAttributes;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder textAttributes(@NotNull TextAttributesKey textAttributes) {
    assertNotSet(this.textAttributesKey, "textAttributes");
    this.textAttributesKey = textAttributes;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder highlightType(@NotNull ProblemHighlightType highlightType) {
    assertNotSet(this.highlightType, "highlightType");
    this.highlightType = highlightType;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder needsUpdateOnTyping() {
    return needsUpdateOnTyping(true);
  }

  @Override
  public @NotNull AnnotationBuilder needsUpdateOnTyping(boolean value) {
    assertNotSet(this.needsUpdateOnTyping, "needsUpdateOnTyping");
    this.needsUpdateOnTyping = value;
    return this;
  }

  @Override
  public @NotNull AnnotationBuilder tooltip(@NotNull String tooltip) {
    assertNotSet(this.tooltip, "tooltip");
    this.tooltip = tooltip;
    return this;
  }

  @Override
  public void create() {
    doCreate();
  }

  private Annotation doCreate() {
    if (created) {
      throw new IllegalStateException("Must not call .create() twice");
    }
    created = true;
    if (range == null) {
      range = myCurrentElement.getTextRange();
    }
    if (tooltip == null && message != null) {
      tooltip = XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(message));
    }
    Annotation annotation = new Annotation(range.getStartOffset(), range.getEndOffset(), severity, message, tooltip);
    if (needsUpdateOnTyping != null) {
      annotation.setNeedsUpdateOnTyping(needsUpdateOnTyping);
    }
    if (highlightType != null) {
      annotation.setHighlightType(highlightType);
    }
    if (textAttributesKey != null) {
      annotation.setTextAttributes(textAttributesKey);
    }
    if (enforcedAttributes != null) {
      annotation.setEnforcedTextAttributes(enforcedAttributes);
    }
    if (problemGroup != null) {
      annotation.setProblemGroup(problemGroup);
    }
    if (gutterIconRenderer != null) {
      annotation.setGutterIconRenderer(gutterIconRenderer);
    }
    if (fileLevel != null) {
      annotation.setFileLevelAnnotation(fileLevel);
    }
    if (afterEndOfLine != null) {
      annotation.setAfterEndOfLine(afterEndOfLine);
    }
    if (fixes != null) {
      for (FixB fb : fixes) {
        CommonIntentionAction fix = fb.fix;
        IntentionAction intention = fix.asIntention();
        TextRange finalRange = fb.range == null ? this.range : fb.range;
        if (fb.batch != null && fb.batch) {
          annotation.registerBatchFix(intention, getLocalQuickFix(fix), finalRange, fb.key);
        }
        else if (fb.universal != null && fb.universal) {
          annotation.registerBatchFix(intention, getLocalQuickFix(fix), finalRange, fb.key);
          annotation.registerFix(intention, finalRange, fb.key);
        }
        else {
          annotation.registerFix(intention, finalRange, fb.key);
        }
      }
    }
    if (myLazyQuickFixes != null) {
      annotation.registerLazyQuickFixes(myLazyQuickFixes);
    }
    myHolder.add(annotation);
    myHolder.annotationCreatedFrom(this);
    return annotation;
  }

  private static @NotNull LocalQuickFix getLocalQuickFix(@NotNull CommonIntentionAction fix) {
    if (fix instanceof ModCommandAction modCommandAction) {
      return LocalQuickFix.from(modCommandAction);
    }
    LocalQuickFix unwrapped = QuickFixWrapper.unwrap(fix);
    if (unwrapped != null) {
      return unwrapped;
    }
    return (LocalQuickFix)fix;
  }

  void assertAnnotationCreated() {
    if (!created) {
      IllegalStateException exception = new IllegalStateException(
        "Abandoned AnnotationBuilder - its 'create()' method was never called: " + this +
        (myDebugCreationPlace == null ? "" : "\nSee cause for the AnnotationBuilder creation stacktrace"), myDebugCreationPlace);
      throw PluginException.createByClass(exception, myCurrentAnnotator.getClass());
    }
  }

  private static @NotNull String omitIfEmpty(Object o, String name) {
    return o == null ? "" : ", " + name + "=" + o;
  }

  @Override
  public String toString() {
    return "Builder{" +
           "message='" + message + '\'' +
           ", myCurrentElement=" + myCurrentElement + " (" + myCurrentElement.getClass() + ")" +
           ", myCurrentAnnotator=" + myCurrentAnnotator +
           ", severity=" + severity +
           ", range=" + (range == null ? "(implicit)" + myCurrentElement.getTextRange() : range) +
           omitIfEmpty(afterEndOfLine, "afterEndOfLine") +
           omitIfEmpty(fileLevel, "fileLevel") +
           omitIfEmpty(gutterIconRenderer, "gutterIconRenderer") +
           omitIfEmpty(problemGroup, "problemGroup") +
           omitIfEmpty(enforcedAttributes, "enforcedAttributes") +
           omitIfEmpty(textAttributesKey, "textAttributesKey") +
           omitIfEmpty(highlightType, "highlightType") +
           omitIfEmpty(needsUpdateOnTyping, "needsUpdateOnTyping") +
           omitIfEmpty(tooltip, "tooltip") +
           omitIfEmpty(fixes, "fixes") +
           '}';
  }

  @Override
  public Annotation createAnnotation() {
    PluginException.reportDeprecatedUsage("AnnotationBuilder#createAnnotation", "Use `#create()` instead");
    return doCreate();
  }
}
