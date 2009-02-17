package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.Set;

/**
 * @author Vladislav.Kaznacheev
 */
public interface ClasspathStorageProvider {
  @NonNls ExtensionPointName<ClasspathStorageProvider> EXTENSION_POINT_NAME =
    new ExtensionPointName<ClasspathStorageProvider>("com.intellij.classpathStorageProvider");

  @NonNls
  String getID();

  @Nls
  String getDescription();

  void assertCompatible(final ModifiableRootModel model) throws ConfigurationException;

  void detach(Module module);

  void moduleRenamed(Module module, String newName);

  ClasspathConverter createConverter(Module module);

  interface ClasspathConverter {

    FileSet getFileSet();

    Set<String> getClasspath(ModifiableRootModel model, final Element element) throws IOException, InvalidDataException;

    void setClasspath(ModifiableRootModel model) throws IOException, WriteExternalException;
  }
}
