package com.intellij.packaging.impl.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditorUtil;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class LibraryElementPresentation extends PackagingElementPresentation {
  private final Library myLibrary;
  private final String myName;
  private final ArtifactEditorContext myContext;

  public LibraryElementPresentation(String level, String name, Library library, ArtifactEditorContext context) {
    myLibrary = library;
    myName = name;
    myContext = context;
  }

  public String getPresentableName() {
    return myName;
  }

  @Override
  public boolean canNavigateToSource() {
    return myLibrary != null;
  }

  @Override
  public Object getSourceObject() {
    return myLibrary;
  }

  @Override
  public void navigateToSource() {
    ProjectStructureConfigurable.getInstance(myContext.getProject()).selectProjectOrGlobalLibrary(myLibrary, true);
  }

  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    if (myLibrary != null) {
      presentationData.setIcons(Icons.LIBRARY_ICON);
      presentationData.addText(myName, mainAttributes);
      presentationData.addText(PackagingEditorUtil.getLibraryTableComment(myLibrary), commentAttributes);
    }
    else {
      presentationData.addText(myName, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.LIBRARY;
  }

}
