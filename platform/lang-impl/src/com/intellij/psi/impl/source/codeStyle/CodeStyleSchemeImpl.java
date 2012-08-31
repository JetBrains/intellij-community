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

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author MYakovlev
 * Date: Jul 17, 2002
 */
public class CodeStyleSchemeImpl implements JDOMExternalizable, CodeStyleScheme, ExternalizableScheme {
  @NonNls private static final String CODE_SCHEME = "code_scheme";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String PARENT = "parent";
  @NonNls private static final String XML_EXTENSION = ".xml";
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl");

  private String myName;
  private Element myRootElement;
  private String myParentSchemeName;
  private final boolean myIsDefault;
  private volatile CodeStyleSettings myCodeStyleSettings;
  private final ExternalInfo myExternalInfo = new ExternalInfo();

  public CodeStyleSchemeImpl(String name, String parentSchemeName, Element rootElement) {
    myName = name;
    myRootElement = rootElement;
    myIsDefault = false;
    myParentSchemeName = parentSchemeName;
  }

  public void init(CodeStyleSchemes schemesManager) {
    LOG.assertTrue(myCodeStyleSettings == null, "Already initialized");
    init(schemesManager.findSchemeByName(myParentSchemeName), myRootElement);
    myParentSchemeName = null;
    myRootElement = null;
  }

  public CodeStyleSchemeImpl(String name, boolean isDefault, CodeStyleScheme parentScheme){
    myName = name;
    myIsDefault = isDefault;
    init(parentScheme, null);
  }

  private void init(CodeStyleScheme parentScheme, Element root) {
    CodeStyleSettings parentSettings = parentScheme == null ? null : parentScheme.getCodeStyleSettings();
    if (parentSettings == null){
      myCodeStyleSettings = new CodeStyleSettings();
    }
    else{
      myCodeStyleSettings = parentSettings.clone();
      while(parentSettings.getParentSettings() != null){
        parentSettings = parentSettings.getParentSettings();
      }
      myCodeStyleSettings.setParentSettings(parentSettings);
    }
    if (root != null) {
      try {
        readExternal(root);
      } catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public CodeStyleSettings getCodeStyleSettings(){
    return myCodeStyleSettings;
  }

  public void setCodeStyleSettings(@NotNull CodeStyleSettings codeStyleSettings){
    myCodeStyleSettings = codeStyleSettings;
  }

  @Override
  public String getName(){
    return myName;
  }

  @Override
  public boolean isDefault(){
    return myIsDefault;
  }

  public String toString(){
    return getName();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException{
    myCodeStyleSettings.writeExternal(element);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException{
    myCodeStyleSettings.readExternal(element);
  }

  public static CodeStyleSchemeImpl readScheme(Document document) throws InvalidDataException, JDOMException, IOException{
    Element root = document.getRootElement();
    if (root == null){
      throw new InvalidDataException("No root element in code style scheme file");
    }

    String schemeName = root.getAttributeValue(NAME);
    String parentName = root.getAttributeValue(PARENT);

    if (schemeName == null) {
      throw new InvalidDataException("Name attribute missing in code style scheme file");
    }

    return new CodeStyleSchemeImpl(schemeName, parentName, root);
  }

  public void save(File dir) throws WriteExternalException{
    Element newElement = new Element(CODE_SCHEME);
    newElement.setAttribute(NAME, getName());
    (this).writeExternal(newElement);

    String filePath = dir.getAbsolutePath() + File.separator + getName() + XML_EXTENSION;
    try {
      JDOMUtil.writeDocument(new Document(newElement), filePath, getCodeStyleSettings().getLineSeparator());
    }
    catch (IOException e) {
      Messages.showErrorDialog(PsiBundle.message("codestyle.cannot.save.scheme.file", filePath, e.getLocalizedMessage()), CommonBundle.getErrorTitle());
    }
  }

  public Document saveToDocument() throws WriteExternalException {
    Element newElement = new Element(CODE_SCHEME);
    newElement.setAttribute(NAME, getName());
    writeExternal(newElement);

    return new Document(newElement);
  }

  @Override
  public void setName(final String name) {
    myName = name;
  }

  @Override
  @NotNull
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }
}
