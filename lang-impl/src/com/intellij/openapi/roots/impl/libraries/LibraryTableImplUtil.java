package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 *  @author dsl
 */
public class LibraryTableImplUtil {
  @NonNls public static final String MODULE_LEVEL = "module";

  private LibraryTableImplUtil() {
  }

  public static Library loadLibrary(Element rootElement, RootModelImpl rootModel) throws InvalidDataException {
    final List children = rootElement.getChildren(LibraryImpl.ELEMENT);
    if (children.size() != 1) throw new InvalidDataException();
    Element element = (Element)children.get(0);
    return new LibraryImpl(null, element, rootModel);
  }

  public static Library createModuleLevelLibrary(@Nullable String name, RootModelImpl rootModel) {
    return new LibraryImpl(name, null, rootModel);
  }
}
