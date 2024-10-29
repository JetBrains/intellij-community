// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DocumentUtil;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateStateBase {

  private final Map myProperties = new HashMap();

  private Editor myEditor;
  private Document myDocument;

  private Map<String, String> myPredefinedVariableValues;

  private TemplateBase myTemplate;
  private TemplateSegments mySegments;

  public TemplateStateBase(Editor editor, @NotNull Document document) {
    myEditor = editor;
    myDocument = document;
  }

  protected void setEditor(Editor editor) {
    myEditor = editor;
  }

  public Editor getEditor() {
    return myEditor;
  }

  protected Document getDocument() {
    return myDocument;
  }

  protected void setDocument(Document document) {
    myDocument = document;
  }

  protected Map<String, String> getPredefinedVariableValues() {
    return myPredefinedVariableValues;
  }

  protected  void setPredefinedVariableValues(Map<String, String> predefinedVariableValues) {
    myPredefinedVariableValues = predefinedVariableValues;
  }

  @ApiStatus.Internal
  protected void setTemplate(TemplateBase template) {
    myTemplate = template;
  }

  protected @Nullable String getSelectionBeforeTemplate() {
    return (String)myProperties.get(ExpressionContext.SELECTION);
  }

  public @Nullable TextResult getVariableValue(@NotNull String variableName) {
    if (variableName.equals(Template.SELECTION)) {
      return new TextResult(StringUtil.notNullize(getSelectionBeforeTemplate()));
    }
    if (variableName.equals(Template.END)) {
      return new TextResult("");
    }
    if (myPredefinedVariableValues != null) {
      String text = myPredefinedVariableValues.get(variableName);
      if (text != null) {
        return new TextResult(text);
      }
    }
    int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
    if (segmentNumber < 0 || mySegments.getSegmentsCount() <= segmentNumber) {
      return null;
    }
    CharSequence text = myDocument.getImmutableCharSequence();
    int start = mySegments.getSegmentStart(segmentNumber);
    int end = mySegments.getSegmentEnd(segmentNumber);
    int length = text.length();
    if (start > length || end > length) {
      return null;
    }
    return new TextResult(text.subSequence(start, end).toString());
  }

  boolean isDisposed() {
    return myDocument == null;
  }

  protected void restoreEmptyVariables(@NotNull List<Integer> indices) {
    List<TextRange> rangesToRemove = new ArrayList<>();
    for (int i = 0; i < indices.size(); i++) {
      int index = indices instanceof IntList ? ((IntList)indices).getInt(i) : indices.get(i);
      rangesToRemove.add(TextRange.create(mySegments.getSegmentStart(index), mySegments.getSegmentEnd(index)));
    }
    rangesToRemove.sort((o1, o2) -> {
      int startDiff = o2.getStartOffset() - o1.getStartOffset();
      return startDiff != 0 ? startDiff : o2.getEndOffset() - o1.getEndOffset();
    });
    DocumentUtil.executeInBulk(myDocument, () -> {
      if (isDisposed()) {
        return;
      }
      for (TextRange range : rangesToRemove) {
        myDocument.deleteString(range.getStartOffset(), range.getEndOffset());
      }
    });
  }

  @ApiStatus.Internal
  protected TemplateBase getTemplate() {
    return myTemplate;
  }

  @ApiStatus.Internal
  protected TemplateSegments getSegments() {
    return mySegments;
  }

  @ApiStatus.Internal
  protected void setSegments(TemplateSegments segments) {
    mySegments = segments;
  }

  public Map getProperties() {
    return myProperties;
  }
}
