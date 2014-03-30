package com.intellij.refactoring.extractMethod;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
* User: ktisha
*/
public class SimpleMatch {
  PsiElement myStartElement;
  PsiElement myEndElement;
  private final Map<String, String> myChangedParameters;
  private String myChangedOutput;

  public SimpleMatch(@NotNull final PsiElement start, @NotNull final PsiElement endElement) {
    myStartElement = start;
    myEndElement = endElement;
    myChangedParameters = new HashMap<String, String>();
  }

  public PsiElement getStartElement() {
    return myStartElement;
  }

  public PsiElement getEndElement() {
    return myEndElement;
  }

  public Map<String, String> getChangedParameters() {
    return myChangedParameters;
  }

  public void changeParameter(@NotNull final String from, @NotNull final String to) {
    myChangedParameters.put(from, to);
  }

  public void changeOutput(@NotNull final String to) {
    myChangedOutput = to;
  }

  public String getChangedOutput() {
    return myChangedOutput;
  }

}
