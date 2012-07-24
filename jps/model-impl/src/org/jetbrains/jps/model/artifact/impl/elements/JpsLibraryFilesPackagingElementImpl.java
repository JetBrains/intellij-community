package org.jetbrains.jps.model.artifact.impl.elements;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.artifact.elements.JpsLibraryFilesPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsOrderRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsLibraryFilesPackagingElementImpl extends JpsComplexPackagingElementBase<JpsLibraryFilesPackagingElementImpl> implements JpsLibraryFilesPackagingElement {
  private static final JpsElementKind<JpsLibraryReference> LIBRARY_REFERENCE_KIND = new JpsElementKindBase<JpsLibraryReference>("library reference");

  public JpsLibraryFilesPackagingElementImpl(@NotNull JpsLibraryReference reference) {
    myContainer.setChild(LIBRARY_REFERENCE_KIND, reference);
  }

  private JpsLibraryFilesPackagingElementImpl(JpsLibraryFilesPackagingElementImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsLibraryFilesPackagingElementImpl createCopy() {
    return new JpsLibraryFilesPackagingElementImpl(this);
  }

  @Override
  @NotNull
  public JpsLibraryReference getLibraryReference() {
    return myContainer.getChild(LIBRARY_REFERENCE_KIND);
  }

  @Override
  public List<JpsPackagingElement> getSubstitution() {
    JpsLibrary library = getLibraryReference().resolve();
    if (library == null) return Collections.emptyList();
    List<JpsPackagingElement> result = new ArrayList<JpsPackagingElement>();
    for (File file : library.getFiles(JpsOrderRootType.COMPILED)) {
      String path = FileUtil.toSystemIndependentName(file.getAbsolutePath());
      if (file.isDirectory()) {
        result.add(JpsPackagingElementFactory.getInstance().createDirectoryCopy(path));
      }
      else {
        result.add(JpsPackagingElementFactory.getInstance().createFileCopy(path, null));
      }
    }
    return result;
  }
}
