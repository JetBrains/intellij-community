/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author mike
 */
public class TemplateBuilderImpl implements TemplateBuilder {
  private final RangeMarker myContainerElement;
  private final Map<RangeMarker,Expression> myExpressions = new HashMap<RangeMarker, Expression>();
  private final Map<RangeMarker,String> myVariableExpressions = new HashMap<RangeMarker, String>();
  private final Map<RangeMarker, Boolean> myAlwaysStopAtMap = new HashMap<RangeMarker, Boolean>();
  private final Map<RangeMarker, String> myVariableNamesMap = new HashMap<RangeMarker, String>();
  private final Set<RangeMarker> myElements = new TreeSet<RangeMarker>(RangeMarker.BY_START_OFFSET);

  private RangeMarker myEndElement;
  private RangeMarker mySelection;
  private final Document myDocument;
  private final PsiFile myFile;

  public TemplateBuilderImpl(@NotNull PsiElement element) {
    myFile = InjectedLanguageUtil.getTopLevelFile(element);
    myDocument = myFile.getViewProvider().getDocument();
    myContainerElement = wrapElement(element);
  }

  public void replaceElement(PsiElement element, Expression expression, boolean alwaysStopAt) {
    final RangeMarker key = wrapElement(element);
    myAlwaysStopAtMap.put(key, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    replaceElement(key, expression);
  }

  private RangeMarker wrapElement(final PsiElement element) {
    return myDocument.createRangeMarker(element.getTextRange().shiftRight(PsiUtilBase.findInjectedElementOffsetInRealDocument(element)));
  }

  private RangeMarker wrapReference(final PsiReference ref) {
    return myDocument.createRangeMarker(ref.getRangeInElement().shiftRight(
      ref.getElement().getTextRange().getStartOffset() + PsiUtilBase.findInjectedElementOffsetInRealDocument(ref.getElement())
    ));
  }

  public void replaceElement(PsiElement element, String varName, Expression expression, boolean alwaysStopAt) {
    final RangeMarker key = wrapElement(element);
    myAlwaysStopAtMap.put(key, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    myVariableNamesMap.put(key, varName);
    replaceElement(key, expression);
  }

  public void replaceElement(PsiReference ref, String varName, Expression expression, boolean alwaysStopAt) {
    final RangeMarker key = wrapReference(ref);
    myAlwaysStopAtMap.put(key, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    myVariableNamesMap.put(key, varName);
    replaceElement(key, expression);
  }

  private void replaceElement(final RangeMarker key, final Expression expression) {
    myExpressions.put(key, expression);
    myElements.add(key);
  }

  public void replaceElement (PsiElement element, String varName, String dependantVariableName, boolean alwaysStopAt) {
    final RangeMarker key = wrapElement(element);
    myAlwaysStopAtMap.put(key, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    myVariableNamesMap.put(key, varName);
    myVariableExpressions.put(key, dependantVariableName);
    myElements.add(key);
  }

  public void replaceElement (PsiReference ref, String varName, String dependantVariableName, boolean alwaysStopAt) {
    final RangeMarker key = wrapReference(ref);
    myAlwaysStopAtMap.put(key, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    myVariableNamesMap.put(key, varName);
    myVariableExpressions.put(key, dependantVariableName);
    myElements.add(key);
  }

  public void replaceElement(PsiElement element, Expression expression) {
    final RangeMarker key = wrapElement(element);
    myExpressions.put(key, expression);
    myElements.add(key);
  }

  public void replaceRange(TextRange rangeWithinElement, String replacementText) {
    final RangeMarker key = myDocument.createRangeMarker(rangeWithinElement.shiftRight(myContainerElement.getStartOffset()));
    myExpressions.put(key, new ConstantNode(replacementText));
    myElements.add(key);
  }

  public void replaceRange(TextRange rangeWithinElement, Expression expression) {
    final RangeMarker key = myDocument.createRangeMarker(rangeWithinElement);
    myExpressions.put(key, expression);
    myElements.add(key);
  }

  /**
   * Adds end variable after the specified element
   */
  public void setEndVariableAfter(PsiElement element) {
    element = element.getNextSibling();
    setEndVariableBefore(element);
  }

  public void setEndVariableBefore(PsiElement element) {
    if (myEndElement != null) {
      myElements.remove(myEndElement);
    }
    myEndElement = wrapElement(element);
    myElements.add(myEndElement);
  }

  public void setSelection(PsiElement element) {
    mySelection = wrapElement(element);
    myElements.add(mySelection);
  }

  public Template buildInlineTemplate() {
    Template template = buildTemplate();
    template.setInline(true);

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    //this is kinda hacky way of doing things, but have not got a better idea
    for (RangeMarker element : myElements) {
      myDocument.deleteString(element.getStartOffset(), element.getEndOffset());
    }

    return template;
  }

  public Template buildTemplate() {
    TemplateManager manager = TemplateManager.getInstance(myFile.getProject());
    final Template template = manager.createTemplate("", "");

    String text = getDocumentTextFragment(myContainerElement.getStartOffset(),myContainerElement.getEndOffset());
    final int containerStart = myContainerElement.getStartOffset();
    int start = 0;
    for (final RangeMarker element : myElements) {
      int offset = element.getStartOffset() - containerStart;
      template.addTextSegment(text.substring(start, offset));

      if (element == mySelection) {
        template.addSelectionStartVariable();
        template.addTextSegment(getDocumentTextFragment(mySelection.getStartOffset(), mySelection.getEndOffset()));
        template.addSelectionEndVariable();
      }
      else if (element == myEndElement) {
        template.addEndVariable();
        start = offset;
        continue;
      }
      else {
        Boolean stop = myAlwaysStopAtMap.get(element);
        final boolean alwaysStopAt = stop == null || stop.booleanValue();
        final Expression expression = myExpressions.get(element);
        final String variableName = myVariableNamesMap.get(element) == null
                                    ? String.valueOf(expression.hashCode())
                                    : myVariableNamesMap.get(element);

        if (expression != null) {
          template.addVariable(variableName, expression, expression, alwaysStopAt);
        }
        else {
          template.addVariableSegment(variableName);
        }
      }

      start = element.getEndOffset() - containerStart;
    }

    template.addTextSegment(text.substring(start));

    for (final RangeMarker element : myElements) {
      final String dependantVariable = myVariableExpressions.get(element);
      if (dependantVariable != null) {
        Boolean stop = myAlwaysStopAtMap.get(element);
        final boolean alwaysStopAt = stop == null || stop.booleanValue();
        final Expression expression = myExpressions.get(element);
        final String variableName = myVariableNamesMap.get(element) == null
                                    ? String.valueOf(expression.hashCode())
                                    : myVariableNamesMap.get(element);
        template.addVariable(variableName, dependantVariable, dependantVariable, alwaysStopAt);
      }
    }

    template.setToIndent(false);
    template.setToReformat(false);

    return template;
  }
  private String getDocumentTextFragment(final int startOffset, final int endOffset) {
    return myDocument.getCharsSequence().subSequence(startOffset, endOffset).toString();
  }

  public void replaceElement(PsiElement element, String replacementText) {
    replaceElement(element, new ConstantNode(replacementText));
  }

  public void replaceElement(PsiElement element, TextRange rangeWithinElement, String replacementText) {
    final RangeMarker key = myDocument.createRangeMarker(rangeWithinElement.shiftRight(element.getTextRange().getStartOffset()));
    myExpressions.put(key, new ConstantNode(replacementText));
    myElements.add(key);
  }

  public void run() {
    final Project project = myFile.getProject();
    VirtualFile file = myFile.getVirtualFile();
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
    final Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);

    final Template template = buildTemplate();

    editor.getDocument().replaceString(myContainerElement.getStartOffset(), myContainerElement.getEndOffset(), "");
    editor.getCaretModel().moveToOffset(myContainerElement.getStartOffset());

    TemplateManager.getInstance(myFile.getProject()).startTemplate(editor, template);
  }
}
