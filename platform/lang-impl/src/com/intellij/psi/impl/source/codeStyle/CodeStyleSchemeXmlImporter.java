// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.ImportSchemeChooserDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.options.SchemeImportUtil;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.Pair.pair;

/**
 * Imports Intellij IDEA code style scheme in XML format.
 * 
 * @author Rustam Vishnyakov
 */
public class CodeStyleSchemeXmlImporter extends CodeStyleSettingsLoader implements SchemeImporter<CodeStyleScheme> {
  @Override
  public String @NotNull [] getSourceExtensions() {
    return new String[]{"xml"};
  }

  @Override
  public @Nullable CodeStyleScheme importScheme(@NotNull Project project,
                                                @NotNull VirtualFile selectedFile,
                                                @NotNull CodeStyleScheme currentScheme,
                                                @NotNull SchemeFactory<? extends CodeStyleScheme> schemeFactory) throws SchemeImportException {
    Element rootElement = SchemeImportUtil.loadSchemeDom(selectedFile);
    Element schemeRoot = findSchemeRoot(rootElement);
    Pair<String, CodeStyleScheme> importPair =
      ApplicationManager.getApplication().isUnitTestMode()
      ? pair(currentScheme.getName(), currentScheme)
      : ImportSchemeChooserDialog.selectOrCreateTargetScheme(project, currentScheme, schemeFactory, getSchemeName(schemeRoot));
    return importPair != null ? readSchemeFromDom(schemeRoot, importPair.second) : null;
  }

  private static @NlsSafe String getSchemeName(@NotNull Element rootElement) throws SchemeImportException {
    String rootName = rootElement.getName();
    if ("value".equals(rootName)) {
      return IdeBundle.message("project.scheme");
    }
    if (!"code_scheme".equals(rootName)) {
      throw new SchemeImportException(ApplicationBundle.message("settings.code.style.import.xml.error.invalid.file", rootName));
    }
    Attribute schemeNameAttr = rootElement.getAttribute("name");
    if (schemeNameAttr == null) {
      throw new SchemeImportException(ApplicationBundle.message("settings.code.style.import.xml.error.missing.scheme.name"));
    }
    return schemeNameAttr.getValue();
  }

  private static CodeStyleScheme readSchemeFromDom(@NotNull Element rootElement, @NotNull CodeStyleScheme scheme) throws SchemeImportException {
    CodeStyleSettings newSettings = CodeStyleSettingsManager.getInstance().createSettings();
    loadSettings(rootElement, newSettings);
    newSettings.resetDeprecatedFields(); // Clean up if imported from legacy settings
    ((CodeStyleSchemeImpl)scheme).setCodeStyleSettings(newSettings);
    return scheme;
  }
}
