package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.rename.RenameProcessor;

import java.util.Collection;
import java.util.Set;

public class RenameRefactoringImpl extends RefactoringImpl<RenameProcessor> {
  public RenameRefactoringImpl(Project project,
                               PsiElement element,
                               String newName,
                               boolean toSearchInComments,
                               boolean toSearchInNonJavaFiles) {
    super(new RenameProcessor(project, element, newName, toSearchInComments, toSearchInNonJavaFiles));
  }

  public void addElement(PsiElement element, String newName) {
    myProcessor.addElement(element, newName);
  }

  public Set<PsiElement> getElements() {
    return myProcessor.getElements();
  }

  public Collection<String> getNewNames() {
    return myProcessor.getNewNames();
  }

  public void setSearchInComments(boolean value) {
    myProcessor.setSearchInComments(value);
  }

  public void setSearchInNonJavaFiles(boolean value) {
    myProcessor.setSearchTextOccurrences(value);
  }

  public boolean isSearchInComments() {
    return myProcessor.isSearchInComments();
  }

  public boolean isSearchInNonJavaFiles() {
    return myProcessor.isSearchTextOccurrences();
  }
}
