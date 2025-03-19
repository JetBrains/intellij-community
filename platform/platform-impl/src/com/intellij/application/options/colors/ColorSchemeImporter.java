// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.options.SchemeImportUtil;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Imports Intellij IDEA color scheme (.icls) to application configuration
 */
@ApiStatus.Internal
public final class ColorSchemeImporter implements SchemeImporter<EditorColorsScheme> {

  private static final String[] FILE_EXTENSIONS =
    new String[] {EditorColorsManager.getColorSchemeFileExtension().substring(1), "jar"};

  @Override
  public String @NotNull [] getSourceExtensions() {
    return FILE_EXTENSIONS;
  }

  @Override
  public @Nullable EditorColorsScheme importScheme(@NotNull Project project,
                                                   @NotNull VirtualFile selectedFile,
                                                   @NotNull EditorColorsScheme currentScheme,
                                                   @NotNull SchemeFactory<? extends EditorColorsScheme> schemeFactory) throws SchemeImportException {
    Element root = SchemeImportUtil.loadSchemeDom(selectedFile);
    String name = getSchemeName(root);
    EditorColorsScheme scheme = schemeFactory.createNewScheme(name);
    String preferredName = scheme.getName();
    scheme.readExternal(root);
    scheme.setName(preferredName);
    try {
      EditorColorsManager.getInstance().resolveSchemeParent(scheme);
    }
    catch (InvalidDataException e) {
      throw new SchemeImportException("Required " + e.getMessage() + " base scheme is missing or is not a bundled (read-only) scheme.");
    }
    return scheme;
  }

  static String getSchemeName(@NotNull Element root) throws SchemeImportException {
    String name = root.getAttributeValue("name");
    if (name == null) throw new SchemeImportException("Scheme 'name' attribute is missing.");
    return name;
  }
}
