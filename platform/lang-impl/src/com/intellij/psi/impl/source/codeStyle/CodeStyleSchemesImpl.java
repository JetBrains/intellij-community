/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.SchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author MYakovlev
 *         Date: Jul 16, 2002
 */
public class CodeStyleSchemesImpl extends CodeStyleSchemes implements ExportableComponent, JDOMExternalizable {
  @NonNls private static final String DEFAULT_SCHEME_NAME = "Default";

  public String CURRENT_SCHEME_NAME = DEFAULT_SCHEME_NAME;
  private boolean myIsInitialized = false;
  @NonNls private static final String CODESTYLES_DIRECTORY = "codestyles";

  private final SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> mySchemesManager;
  @NonNls private static final String FILE_SPEC = "$ROOT_CONFIG$/" + CODESTYLES_DIRECTORY;

  public CodeStyleSchemesImpl(SchemesManagerFactory schemesManagerFactory) {
    SchemeProcessor<CodeStyleSchemeImpl> processor = new BaseSchemeProcessor<CodeStyleSchemeImpl>() {
      public CodeStyleSchemeImpl readScheme(final Document schemeContent) throws IOException, JDOMException, InvalidDataException {
        return CodeStyleSchemeImpl.readScheme(schemeContent);
      }

      public Document writeScheme(final CodeStyleSchemeImpl scheme) throws WriteExternalException {
        return scheme.saveToDocument();
      }

      public boolean shouldBeSaved(final CodeStyleSchemeImpl scheme) {
        return !scheme.isDefault();
      }

      public void initScheme(final CodeStyleSchemeImpl scheme) {
        scheme.init(CodeStyleSchemesImpl.this);
      }
    };

    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, processor, RoamingType.PER_USER);

    init();
    addScheme(new CodeStyleSchemeImpl(DEFAULT_SCHEME_NAME, true, null));
    CodeStyleScheme current = findSchemeByName(CURRENT_SCHEME_NAME);
    if (current == null) current = getDefaultScheme();
    setCurrentScheme(current);
  }

  public CodeStyleScheme[] getSchemes() {
    final Collection<CodeStyleScheme> schemes = mySchemesManager.getAllSchemes();
    return schemes.toArray(new CodeStyleScheme[schemes.size()]);
  }

  public CodeStyleScheme getCurrentScheme() {
    return mySchemesManager.getCurrentScheme();
  }

  public void setCurrentScheme(CodeStyleScheme scheme) {
    mySchemesManager.setCurrentSchemeName(scheme == null ? null : scheme.getName());
    CURRENT_SCHEME_NAME = scheme.getName();
  }

  public CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme) {
    String name;
    if (preferredName == null) {
      // Generate using parent name
      name = null;
      for (int i = 1; name == null; i++) {
        String currName = parentScheme.getName() + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    else {
      name = null;
      for (int i = 0; name == null; i++) {
        String currName = i == 0 ? preferredName : preferredName + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    return new CodeStyleSchemeImpl(name, false, parentScheme);
  }

  public void deleteScheme(CodeStyleScheme scheme) {
    if (scheme.isDefault()) {
      throw new IllegalArgumentException("Unable to delete default scheme!");
    }
    CodeStyleSchemeImpl currScheme = (CodeStyleSchemeImpl)getCurrentScheme();
    if (currScheme == scheme) {
      CodeStyleScheme newCurrentScheme = getDefaultScheme();
      if (newCurrentScheme == null) {
        throw new IllegalStateException("Unable to load default scheme!");
      }
      setCurrentScheme(newCurrentScheme);
    }
    mySchemesManager.removeScheme(scheme);
  }

  public CodeStyleScheme getDefaultScheme() {
    return findSchemeByName(DEFAULT_SCHEME_NAME);
  }

  public CodeStyleScheme findSchemeByName(String name) {
    return mySchemesManager.findSchemeByName(name);
  }

  public void addScheme(CodeStyleScheme scheme) {
    mySchemesManager.addNewScheme(scheme, true);
  }

  protected void removeScheme(CodeStyleScheme scheme) {
    mySchemesManager.removeScheme(scheme);
  }

  public void readExternal(Element element) throws InvalidDataException {
    init();
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  private void init() {
    if (myIsInitialized) return;
    myIsInitialized = true;
    mySchemesManager.loadSchemes();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getDir(true), PathManager.getDefaultOptionsFile()};
  }

  @NotNull
  public String getPresentableName() {
    return PsiBundle.message("codestyle.export.display.name");
  }

  private static File getDir(boolean create) {
    String directoryPath = PathManager.getConfigPath() + File.separator + CODESTYLES_DIRECTORY;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        Messages.showErrorDialog(PsiBundle.message("codestyle.cannot.save.settings.directory.cant.be.created.message", directoryPath),
                                 PsiBundle.message("codestyle.cannot.save.settings.directory.cant.be.created.title"));
        return null;
      }
    }
    return directory;
  }

  public SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> getSchemesManager() {
    return mySchemesManager;
  }
}
