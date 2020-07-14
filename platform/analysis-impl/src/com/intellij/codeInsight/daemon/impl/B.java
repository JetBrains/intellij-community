// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class B implements AnnotationBuilder {
  @NotNull
  private final AnnotationHolderImpl myHolder;
  private final String message;
  @NotNull
  private final PsiElement myCurrentElement;
  @NotNull
  private final HighlightSeverity severity;
  private TextRange range;
  private Boolean afterEndOfLine;
  private Boolean fileLevel;
  private GutterIconRenderer gutterIconRenderer;
  private ProblemGroup problemGroup;
  private TextAttributes enforcedAttributes;
  private TextAttributesKey textAttributes;
  private ProblemHighlightType highlightType;
  private Boolean needsUpdateOnTyping;
  private String tooltip;
  private List<FixB> fixes;
  private boolean created;
  private final Throwable myDebugCreationPlace;

  B(@NotNull AnnotationHolderImpl holder, @NotNull HighlightSeverity severity, String message, @NotNull PsiElement currentElement) {
    myHolder = holder;
    this.severity = severity;
    this.message = message;
    myCurrentElement = currentElement;
    holder.annotationBuilderCreated(this);

    Application app = ApplicationManager.getApplication();
    myDebugCreationPlace = app.isUnitTestMode() && !ApplicationInfoImpl.isInStressTest() || app.isInternal() ?
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

  class FixB implements FixBuilder {
    @NotNull
    IntentionAction fix;
    TextRange range;
    HighlightDisplayKey key;
    Boolean batch;
    Boolean universal;

    FixB(@NotNull IntentionAction fix) {
      this.fix = fix;
    }

    @NotNull
    @Override
    public FixBuilder range(@NotNull TextRange range) {
      assertNotSet(this.range, "range");
      this.range = range;
      return this;
    }

    @NotNull
    @Override
    public FixBuilder key(@NotNull HighlightDisplayKey key) {
      assertNotSet(this.key, "key");
      this.key = key;
      return this;
    }

    @NotNull
    @Override
    public FixBuilder batch() {
      assertNotSet(this.universal, "universal");
      assertNotSet(this.batch, "batch");
      assertLQF();
      this.batch = true;
      return this;
    }

    private void assertLQF() {
      if (!(fix instanceof LocalQuickFix || fix instanceof LocalQuickFixAsIntentionAdapter)) {
        markNotAbandoned();
        throw new IllegalArgumentException("Fix " + fix + " must be instance of LocalQuickFix to be registered as batch");
      }
    }

    @NotNull
    @Override
    public FixBuilder universal() {
      assertNotSet(this.universal, "universal");
      assertNotSet(this.batch, "batch");
      assertLQF();
      this.universal = true;
      return this;
    }

    @NotNull
    @Override
    public AnnotationBuilder registerFix() {
      if (fixes == null) {
        fixes = new ArrayList<>();
      }
      fixes.add(this);
      return B.this;
    }

    @Override
    public String toString() {
      return fix+(range==null?"":" at "+range)+(batch == null ? "" : " batch")+(universal == null ? "" : " universal");
    }
  }

  @NotNull
  @Override
  public AnnotationBuilder withFix(@NotNull IntentionAction fix) {
    return newFix(fix).registerFix();
  }

  @NotNull
  @Override
  public FixBuilder newFix(@NotNull IntentionAction fix) {
    return new FixB(fix);
  }

  @NotNull
  @Override
  public FixBuilder newLocalQuickFix(@NotNull LocalQuickFix fix, @NotNull ProblemDescriptor problemDescriptor) {
    return new FixB(new LocalQuickFixAsIntentionAdapter(fix, problemDescriptor));
  }

  @NotNull
  @Override
  public AnnotationBuilder range(@NotNull TextRange range) {
    assertNotSet(this.range, "range");
    TextRange currentElementRange = myCurrentElement.getTextRange();
    if (!currentElementRange.contains(range)) {
      markNotAbandoned();
      throw PluginException.createByClass("Range must be inside element being annotated: " + currentElementRange + "; but got: " + range,
                                          null, myCurrentElement.getClass());
    }

    this.range = range;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder range(@NotNull ASTNode element) {
    return range(element.getTextRange());
  }

  @NotNull
  @Override
  public AnnotationBuilder range(@NotNull PsiElement element) {
    return range(element.getTextRange());
  }

  @NotNull
  @Override
  public AnnotationBuilder afterEndOfLine() {
    assertNotSet(afterEndOfLine, "afterEndOfLine");
    afterEndOfLine = true;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder fileLevel() {
    assertNotSet(fileLevel, "fileLevel");
    fileLevel = true;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer) {
    assertNotSet(this.gutterIconRenderer, "gutterIconRenderer");
    this.gutterIconRenderer = gutterIconRenderer;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder problemGroup(@NotNull ProblemGroup problemGroup) {
    assertNotSet(this.problemGroup, "problemGroup");
    this.problemGroup = problemGroup;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder enforcedTextAttributes(@NotNull TextAttributes enforcedAttributes) {
    assertNotSet(this.enforcedAttributes, "enforcedAttributes");
    this.enforcedAttributes = enforcedAttributes;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder textAttributes(@NotNull TextAttributesKey textAttributes) {
    assertNotSet(this.textAttributes, "textAttributes");
    this.textAttributes = textAttributes;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder highlightType(@NotNull ProblemHighlightType highlightType) {
    assertNotSet(this.highlightType, "highlightType");
    this.highlightType = highlightType;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder needsUpdateOnTyping() {
    return needsUpdateOnTyping(true);
  }

  @NotNull
  @Override
  public AnnotationBuilder needsUpdateOnTyping(boolean value) {
    assertNotSet(this.needsUpdateOnTyping, "needsUpdateOnTyping");
    this.needsUpdateOnTyping = value;
    return this;
  }

  @NotNull
  @Override
  public AnnotationBuilder tooltip(@NotNull String tooltip) {
    assertNotSet(this.tooltip, "tooltip");
    this.tooltip = tooltip;
    return this;
  }

  @Override
  public void create() {
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
    if (textAttributes != null) {
      annotation.setTextAttributes(textAttributes);
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
        IntentionAction fix = fb.fix;
        TextRange finalRange = fb.range == null ? this.range : fb.range;
        if (fb.batch != null && fb.batch) {
          registerBatchFix(annotation, fix, finalRange, fb.key);
        }
        else if (fb.universal != null && fb.universal) {
          registerBatchFix(annotation, fix, finalRange, fb.key);
          annotation.registerFix(fix, finalRange, fb.key);
        }
        else {
          annotation.registerFix(fix, finalRange, fb.key);
        }
      }
    }
    myHolder.add(annotation);
    myHolder.queueToUpdateIncrementally();
    myHolder.annotationCreatedFrom(this);
  }

  private static <T extends IntentionAction & LocalQuickFix>
  void registerBatchFix(@NotNull Annotation annotation, @NotNull Object fix, @NotNull TextRange range, HighlightDisplayKey key) {
    //noinspection unchecked
    annotation.registerBatchFix((T)fix, range, key);
  }

  void assertAnnotationCreated() {
    if (!created) {
      throw new IllegalStateException("Abandoned AnnotationBuilder - its 'create()' method was never called: "+this
                                      +(myDebugCreationPlace == null ? "" : "\nSee cause for the AnnotationBuilder creation stacktrace"), myDebugCreationPlace);
    }
  }

  private static String omitIfEmpty(Object o, String name) {
    return o == null ? "" : ", " + name + "=" + o;
  }
  @Override
  public String toString() {
    return "Builder{" +
           "message='" + message + '\'' +
           ", myCurrentElement=" + myCurrentElement +
           ", severity=" + severity +
           ", range=" + (range == null ? "(implicit)"+myCurrentElement.getTextRange() : range) +
           omitIfEmpty(afterEndOfLine, "afterEndOfLine") +
           omitIfEmpty(fileLevel, "fileLevel") +
           omitIfEmpty(gutterIconRenderer, "gutterIconRenderer") +
           omitIfEmpty(problemGroup, "problemGroup") +
           omitIfEmpty(enforcedAttributes, "enforcedAttributes") +
           omitIfEmpty(textAttributes, "textAttributes") +
           omitIfEmpty(highlightType, "highlightType") +
           omitIfEmpty(needsUpdateOnTyping, "needsUpdateOnTyping") +
           omitIfEmpty(tooltip, "tooltip") +
           omitIfEmpty(fixes, "fixes") +
           '}';
  }
}
