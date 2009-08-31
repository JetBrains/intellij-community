package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author nik
 */
@Tag("packaging-tree")
public class PackagingTreeParameters {
  private boolean myShowIncludedContent;
  private boolean myShowLibraryFiles;

  public PackagingTreeParameters() {
  }

  public PackagingTreeParameters(final boolean showIncludedContent, final boolean showLibraryFiles) {
    myShowIncludedContent = showIncludedContent;
    myShowLibraryFiles = showLibraryFiles;
  }

  @Attribute("show-included-content")
  public boolean isShowIncludedContent() {
    return myShowIncludedContent;
  }

  @Attribute("show-library-files")
  public boolean isShowLibraryFiles() {
    return myShowLibraryFiles;
  }

  public void setShowIncludedContent(final boolean showIncludedContent) {
    myShowIncludedContent = showIncludedContent;
  }

  public void setShowLibraryFiles(final boolean showLibraryFiles) {
    myShowLibraryFiles = showLibraryFiles;
  }
}
