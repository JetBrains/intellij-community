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
  private boolean myIsDefault;
  private CodeStyleSettings myCodeStyleSettings;
  private ExternalInfo myExternalInfo = new ExternalInfo();

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

  public CodeStyleSettings getCodeStyleSettings(){
    return myCodeStyleSettings;
  }

  public void setCodeStyleSettings(@NotNull CodeStyleSettings codeStyleSettings){
    myCodeStyleSettings = codeStyleSettings;
  }

  public String getName(){
    return myName;
  }

  public boolean isDefault(){
    return myIsDefault;
  }

  public String toString(){
    return getName();
  }

  public void writeExternal(Element element) throws WriteExternalException{
    myCodeStyleSettings.writeExternal(element);
  }

  public void readExternal(Element element) throws InvalidDataException{
    myCodeStyleSettings.readExternal(element);
  }

  public static CodeStyleSchemeImpl readScheme(Document document) throws InvalidDataException, JDOMException, IOException{
    Element root = document.getRootElement();
    if (root == null){
      throw new InvalidDataException();
    }

    String schemeName = root.getAttributeValue(NAME);
    String parentName = root.getAttributeValue(PARENT);

    if (schemeName == null){
      throw new InvalidDataException();
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

  public void setName(final String name) {
    myName = name;
  }

  @NotNull
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }
}
