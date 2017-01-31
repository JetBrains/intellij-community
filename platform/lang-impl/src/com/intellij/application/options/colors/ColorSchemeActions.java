/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.colors;

import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.AbstractSchemesPanel;
import com.intellij.application.options.schemes.SchemeNameGenerator;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.options.SchemeImportUtil;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.openapi.options.SchemeImporterEP;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ColorSchemeActions extends AbstractSchemeActions<EditorColorsScheme> {

  protected ColorSchemeActions(@NotNull AbstractSchemesPanel<EditorColorsScheme> schemesPanel) {
    super(schemesPanel);
  }

  @Override
  protected Collection<String> getSchemeImportersNames() {
    List<String> importersNames = new ArrayList<>();
    for (ImportHandler importHandler : Extensions.getExtensions(ImportHandler.EP_NAME)) {
      importersNames.add(importHandler.getTitle());
    }
    importersNames.addAll(super.getSchemeImportersNames());
    return importersNames;
  }

  @Override
  protected void importScheme(@NotNull String importerName) {
    if (tryImportWithImportHandler(importerName)) {
      return;
    }
    final SchemeImporter<EditorColorsScheme> importer = SchemeImporterEP.getImporter(importerName, EditorColorsScheme.class);
    if (importer != null) {
      VirtualFile importSource = SchemeImportUtil.selectImportSource(importer.getSourceExtensions(), getSchemesPanel(), null);
      if (importSource != null) {
        try {
          EditorColorsScheme imported =
            importer.importScheme(DefaultProjectFactory.getInstance().getDefaultProject(), importSource, getOptions().getSelectedScheme(),
                                  name -> {
                                    String newName = SchemeNameGenerator.getUniqueName(name != null ? name : "Unnamed",
                                                                                       candidate -> getSchemesPanel().getModel()
                                                                                         .containsScheme(candidate));
                                    AbstractColorsScheme newScheme = new EditorColorsSchemeImpl(EmptyColorScheme.INSTANCE);
                                    newScheme.setName(newName);
                                    newScheme.setDefaultMetaInfo(EmptyColorScheme.INSTANCE);
                                    return newScheme;
                                  });
          if (imported != null) {
            getOptions().addImportedScheme(imported);
          }
        }
        catch (SchemeImportException e) {
          SchemeImportUtil.showStatus(getSchemesPanel(), "Import failed: " + e.getMessage(), MessageType.ERROR);
        }
      }
    }
  }
  
  private boolean tryImportWithImportHandler(@NotNull String importerName) {
     for (ImportHandler importHandler : Extensions.getExtensions(ImportHandler.EP_NAME)) {
       if (importerName.equals(importHandler.getTitle())) {
         importHandler.performImport(getSchemesPanel(), scheme -> {
           if (scheme != null) getOptions().addImportedScheme(scheme);
         });
         return true;
       }
    }
    return false;
  }

  @Override
  protected void resetScheme(@NotNull EditorColorsScheme scheme) {
      if (Messages
            .showOkCancelDialog(ApplicationBundle.message("color.scheme.reset.message"),
                                ApplicationBundle.message("color.scheme.reset.title"), Messages.getQuestionIcon()) == Messages.OK) {
        getOptions().resetSchemeToOriginal(scheme.getName());
      }
  }

  @Override
  protected void duplicateScheme(@NotNull  EditorColorsScheme scheme, @NotNull String newName) {
      getOptions().saveSchemeAs(scheme, newName);
  }

  @Override
  protected void deleteScheme(@NotNull EditorColorsScheme scheme) {
    getOptions().removeScheme(scheme.getName());
  }

  @Override
  protected void exportScheme(@NotNull EditorColorsScheme scheme, @NotNull String exporterName) {
    // Unsupported for now.
  }

  @Override
  protected Class<EditorColorsScheme> getSchemeType() {
    return EditorColorsScheme.class;
  }
  
  @NotNull
  protected abstract ColorAndFontOptions getOptions();
}
