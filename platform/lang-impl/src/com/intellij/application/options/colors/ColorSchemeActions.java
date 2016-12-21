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

import com.intellij.application.options.SaveSchemeDialog;
import com.intellij.application.options.schemes.DefaultSchemeActions;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme;
import com.intellij.openapi.editor.colors.impl.ReadOnlyColorsScheme;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ColorSchemeActions extends DefaultSchemeActions<EditorColorsScheme> {

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
  protected void doImport(@NotNull String importerName) {
    if (tryImportWithImportHandler(importerName)) {
      return;
    }
    final SchemeImporter<EditorColorsScheme> importer = SchemeImporterEP.getImporter(importerName, EditorColorsScheme.class);
    if (importer != null) {
      VirtualFile importSource = SchemeImportUtil.selectImportSource(importer.getSourceExtensions(), getParentComponent(), null);
      if (importSource != null) {
        try {
          EditorColorsScheme imported =
            importer.importScheme(DefaultProjectFactory.getInstance().getDefaultProject(), importSource, getOptions().getSelectedScheme(),
                                  name -> {
                                    String newName = getOptions().getUniqueName(name != null ? name : "Unnamed");
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
          SchemeImportUtil.showStatus(getParentComponent(), "Import failed: " + e.getMessage(), MessageType.ERROR);
        }
      }
    }
  }
  
  private boolean tryImportWithImportHandler(@NotNull String importerName) {
     for (ImportHandler importHandler : Extensions.getExtensions(ImportHandler.EP_NAME)) {
       if (importerName.equals(importHandler.getTitle())) {
         importHandler.performImport(getParentComponent(), scheme -> {
           if (scheme != null) getOptions().addImportedScheme(scheme);
         });
         return true;
       }
    }
    return false;
  }

  @Override
  protected void doReset(@NotNull EditorColorsScheme scheme) {
      if (Messages
            .showOkCancelDialog(ApplicationBundle.message("color.scheme.reset.message"),
                                ApplicationBundle.message("color.scheme.reset.title"), Messages.getQuestionIcon()) == Messages.OK) {
        getOptions().resetSchemeToOriginal(scheme.getName());
      }
  }

  @Override
  protected void doSaveAs(@NotNull  EditorColorsScheme scheme) {
    List<String> names = ContainerUtil.newArrayList(getOptions().getSchemeNames());
    String selectedName = AbstractColorsScheme.getDisplayName(scheme);
    SaveSchemeDialog dialog =
      new SaveSchemeDialog(getParentComponent(), ApplicationBundle.message("title.save.color.scheme.as"), names, selectedName);
    if (dialog.showAndGet()) {
      getOptions().saveSchemeAs(dialog.getSchemeName());
    }
  }

  @Override
  protected void doDelete(@NotNull EditorColorsScheme scheme) {
    getOptions().removeScheme(scheme.getName());
  }

  @Override
  protected boolean isDeleteAvailable(@NotNull EditorColorsScheme scheme) {
    return !ColorAndFontOptions.isReadOnly(scheme) && ColorAndFontOptions.canBeDeleted(scheme);
  }
  
  @Override
  protected boolean isResetAvailable(@NotNull EditorColorsScheme scheme) {
    AbstractColorsScheme originalScheme =
      scheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)scheme).getOriginal() : null;
    return
      !ColorAndFontOptions.isReadOnly(scheme) &&
      scheme.getName().startsWith(SchemeManager.EDITABLE_COPY_PREFIX) &&
      originalScheme instanceof ReadOnlyColorsScheme;
  }

  @Override
  protected void doExport(@NotNull EditorColorsScheme scheme, @NotNull String exporterName) {
    // Unsupported for now.
  }

  @Override
  protected Class<EditorColorsScheme> getSchemeType() {
    return EditorColorsScheme.class;
  }
  
  @NotNull
  protected abstract ColorAndFontOptions getOptions();
}
