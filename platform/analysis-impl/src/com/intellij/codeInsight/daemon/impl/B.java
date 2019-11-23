// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.*;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class B implements AnnotationBuilder {
  @NotNull
  private final AnnotationHolderImpl myHolder;
  private final String message;
  private final HighlightSeverity severity;
  private final TextRange range;
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

  B(@NotNull AnnotationHolderImpl holder, TextRange range, HighlightSeverity severity, String message) {
    myHolder = holder;
    this.severity = severity;
    this.message = message;
    this.range = range;
    if (getClass() != B.class) {
      throw new IncorrectOperationException("You must not extend AnnotationHolder.Builder");
    }
  }

  private static void assertNotSet(Object o, String description) {
    if (o != null) {
      throw new IllegalStateException(description + " was set already");
    }
  }

  class FixB implements FixBuilder {
    @NotNull
    IntentionAction fix;
    TextRange range;
    HighlightDisplayKey key;
    Boolean batch;

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
      assertNotSet(this.batch, "batch");
      this.batch = true;
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
    assertNotSet(this.needsUpdateOnTyping, "needsUpdateOnTyping");
    this.needsUpdateOnTyping = true;
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
    //assertSet(range, "range");
    //assertSet(severity, "severity");
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
        else {
          annotation.registerFix(fix, finalRange, fb.key);
        }
      }
    }
    myHolder.add(annotation);
    myHolder.queueToUpdateIncrementally();
  }

  private static <T extends IntentionAction & LocalQuickFix>
  void registerBatchFix(@NotNull Annotation annotation, @NotNull Object fix, @NotNull TextRange range, HighlightDisplayKey key) {
    //noinspection unchecked
    annotation.registerBatchFix((T)fix, range, key);
  }
}
