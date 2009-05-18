package com.intellij.compiler.artifacts;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.elements.*;

/**
 * @author nik
 */
public abstract class PackagingElementsTestCase extends ArtifactsTestCase {

  protected void assertLayout(PackagingElement element, String expected) {
    assertEquals(expected, printToString(element, 0));
  }

  private static String printToString(PackagingElement element, int level) {
    StringBuilder builder = new StringBuilder(StringUtil.repeatSymbol(' ', level));
    if (element instanceof ArchivePackagingElement) {
      builder.append(((ArchivePackagingElement)element).getArchiveFileName());
    }
    else if (element instanceof DirectoryPackagingElement) {
      builder.append(((DirectoryPackagingElement)element).getDirectoryName()).append("/");
    }
    else if (element instanceof ArtifactPackagingElement) {
      builder.append("artifact:").append(((ArtifactPackagingElement)element).getArtifactName());
    }
    else if (element instanceof LibraryPackagingElement) {
      builder.append("lib:").append(((LibraryPackagingElement)element).getName());
    }
    else if (element instanceof ModuleOutputPackagingElement) {
      builder.append("module:").append(((ModuleOutputPackagingElement)element).getModuleName());
    }
    else if (element instanceof FileCopyPackagingElement) {
      builder.append("file:").append(((FileCopyPackagingElement)element).getFilePath());
    }
    builder.append("\n");
    if (element instanceof CompositePackagingElement) {
      for (PackagingElement<?> child : ((CompositePackagingElement<?>)element).getChildren()) {
        builder.append(printToString(child, level + 1));
      }
    }
    return builder.toString();
  }

  protected PackagingElementFactory getFactory() {
    return PackagingElementFactory.getInstance();
  }
}
