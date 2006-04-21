package com.intellij.codeInsight.template;

import com.intellij.psi.*;
import com.intellij.openapi.editor.Document;import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationManager;

import java.util.*;import gnu.trove.THashMap;

/**
 * @author mike
 */
public class TemplateBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.TemplateBuilder");
  private RangeMarker myContainerElement;
  private Map<RangeMarker,Expression> myExpressions = new HashMap<RangeMarker, Expression>();
  private Map<RangeMarker,String> myVariableExpressions = new HashMap<RangeMarker, String>();
  private Map<RangeMarker, Boolean> myAlwaysStopAtMap = new HashMap<RangeMarker, Boolean>();
  private Map<RangeMarker, String> myVariableNamesMap = new HashMap<RangeMarker, String>();
  private Set<RangeMarker> myElements = new TreeSet<RangeMarker>(new Comparator<RangeMarker>() {
    public int compare(final RangeMarker e1, final RangeMarker e2) {
      return e1.getStartOffset() - e2.getStartOffset();
    }
  });

  private RangeMarker myEndElement;
  private RangeMarker mySelection;
  private final Document myDocument;
  private PsiFile myFile;

  public TemplateBuilder(PsiElement element) {
    myFile = element.getContainingFile();
    myDocument = myFile.getViewProvider().getDocument();
    myContainerElement = wrapElement(element);
  }

  public void replaceElement(PsiElement element, Expression expression, boolean alwaysStopAt) {
    final RangeMarker key = wrapElement(element);
    myAlwaysStopAtMap.put(key, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    replaceElement(key, expression);
  }

  private RangeMarker wrapElement(final PsiElement element) {
    return myDocument.createRangeMarker(element.getTextRange());
  }

  public void replaceElement(PsiElement element, String varName, Expression expression, boolean alwaysStopAt) {
    final RangeMarker key = wrapElement(element);
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

  public void replaceElement(PsiElement element, Expression expression) {
    final RangeMarker key = wrapElement(element);
    myExpressions.put(key, expression);
    myElements.add(key);
  }

  /**
   * Adds end variable after the specified element
   */
  public void setEndVariableAfter(PsiElement element) {
    if (myEndElement != null) myElements.remove(myEndElement);
    element = element.getNextSibling();
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
        final boolean alwaysStopAt = myAlwaysStopAtMap.get(element) == null || myAlwaysStopAtMap.get(element);
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
        final boolean alwaysStopAt = myAlwaysStopAtMap.get(element) == null || myAlwaysStopAtMap.get(element);
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
}
