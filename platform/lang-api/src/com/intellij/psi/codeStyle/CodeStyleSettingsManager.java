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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleSettingsManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#" + CodeStyleSettingsManager.class.getName());

  public volatile CodeStyleSettings PER_PROJECT_SETTINGS;
  public volatile boolean USE_PER_PROJECT_SETTINGS;
  public volatile String PREFERRED_PROJECT_CODE_STYLE;
  private volatile CodeStyleSettings myTemporarySettings;
  private volatile boolean myIsLoaded;

  public static CodeStyleSettingsManager getInstance(@Nullable Project project) {
    if (project == null || project.isDefault()) return getInstance();
    ProjectCodeStyleSettingsManager projectSettingsManager = ServiceManager.getService(project, ProjectCodeStyleSettingsManager.class);
    if (!projectSettingsManager.isLoaded()) {
      synchronized (projectSettingsManager) {
        if (!projectSettingsManager.isLoaded()) {
          LegacyCodeStyleSettingsManager legacySettingsManager = ServiceManager.getService(project, LegacyCodeStyleSettingsManager.class);
          if (legacySettingsManager != null && legacySettingsManager.getState() != null) {
            projectSettingsManager.loadState(legacySettingsManager.getState());
            LOG.info("Imported old project code style settings.");
          }
        }
      }
    }
    return projectSettingsManager;
  }

  public static CodeStyleSettingsManager getInstance() {
    return ServiceManager.getService(AppCodeStyleSettingsManager.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public CodeStyleSettingsManager(Project project) {
  }
  public CodeStyleSettingsManager() {}

  @NotNull
  public static CodeStyleSettings getSettings(@Nullable final Project project) {
    return getInstance(project).getCurrentSettings();
  }

  @NotNull
  public CodeStyleSettings getCurrentSettings() {
    CodeStyleSettings temporarySettings = myTemporarySettings;
    if (temporarySettings != null) return temporarySettings;
    CodeStyleSettings projectSettings = PER_PROJECT_SETTINGS;
    if (USE_PER_PROJECT_SETTINGS && projectSettings != null) return projectSettings;
    return CodeStyleSchemes.getInstance().findPreferredScheme(PREFERRED_PROJECT_CODE_STYLE).getCodeStyleSettings();
  }

  @Override
  public Element getState() {
    Element result = new Element("state");
    try {
      DefaultJDOMExternalizer.writeExternal(this, result, new DifferenceFilter<>(this, new CodeStyleSettingsManager()));
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return result;
  }

  @Override
  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
      myIsLoaded = true;
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public CodeStyleSettings getTemporarySettings() {
    return myTemporarySettings;
  }

  /**
   * @see #dropTemporarySettings()
   */
  public void setTemporarySettings(@NotNull CodeStyleSettings settings) {
    myTemporarySettings = settings;
  }

  public void dropTemporarySettings() {
    myTemporarySettings = null;
  }

  public boolean isLoaded() {
    return myIsLoaded;
  }

  /**
   * Updates document's indent options from indent options providers.
   * <p><b>Note:</b> Calling this method directly when there is an editor associated with the document may cause the editor work
   * incorrectly. To keep consistency with the editor call <code>EditorEx.reinitSettings()</code> instead.
   * @param project  The project of the document.
   * @param document The document to update indent options for.
   */
  public static void updateDocumentIndentOptions(@NotNull Project project, @NotNull Document document) {
    if (!project.isDisposed()) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      if (documentManager != null) {
        PsiFile file = documentManager.getPsiFile(document);
        if (file != null) {
          CommonCodeStyleSettings.IndentOptions indentOptions = getSettings(project).getIndentOptionsByFile(file, null, true, null);
          indentOptions.associateWithDocument(document);
        }
      }
    }
  }
}
