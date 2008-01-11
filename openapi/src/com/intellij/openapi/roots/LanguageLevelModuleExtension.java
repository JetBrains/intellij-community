/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class LanguageLevelModuleExtension extends ModuleExtension<LanguageLevelModuleExtension> {
  @NonNls private static final String LANGUAGE_LEVEL_ELEMENT_NAME = "LANGUAGE_LEVEL";
  private Module myModule;
  private boolean myWritable;
  private static final Logger LOG = Logger.getInstance("#" + LanguageLevelModuleExtension.class.getName());

  public static LanguageLevelModuleExtension getInstance(final Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
  }

  private LanguageLevel myLanguageLevel;
  private LanguageLevelModuleExtension mySource;

  public LanguageLevelModuleExtension(Module module) {
    myModule = module;
  }

  public LanguageLevelModuleExtension(final LanguageLevelModuleExtension source, boolean writable) {
    myWritable = writable;
    myModule = source.myModule;
    myLanguageLevel = source.myLanguageLevel;
    mySource = source;
  }

  public void setLanguageLevel(final LanguageLevel languageLevel) {
    LOG.assertTrue(myWritable, "Writable model can be retrieved from writable ModifiableRootModel");
    if (mySource == null && myModule.isLoaded()) { //do not reload project for non-commited modules: j2me|project imports
      if (myLanguageLevel != languageLevel) {
        final LanguageLevelProjectExtension languageLevelProjectExtension = LanguageLevelProjectExtension.getInstance(myModule.getProject());
        final LanguageLevel projectLanguageLevel = languageLevelProjectExtension.getLanguageLevel();
        if (!(languageLevel == null && myLanguageLevel == projectLanguageLevel || 
             (myLanguageLevel == null && languageLevel == projectLanguageLevel))) {
          languageLevelProjectExtension.reloadProjectOnLanguageLevelChange(languageLevel, true);
        }
      }
    }
    myLanguageLevel = languageLevel;
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    final String languageLevel = element.getAttributeValue(LANGUAGE_LEVEL_ELEMENT_NAME);
    if (languageLevel != null) {
      try {
        myLanguageLevel = LanguageLevel.valueOf(languageLevel);
      }
      catch (IllegalArgumentException e) {
        //bad value was stored
      }
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    if (myLanguageLevel != null) {
      element.setAttribute(LANGUAGE_LEVEL_ELEMENT_NAME, myLanguageLevel.toString());
    }
  }

  public ModuleExtension getModifiableModel(final boolean writable) {
    return new LanguageLevelModuleExtension(this, writable);
  }

  public void commit() {
    if (mySource != null) {
      mySource.myLanguageLevel = myLanguageLevel;
    }
  }

  public boolean isChanged() {
    return mySource != null && mySource.myLanguageLevel != myLanguageLevel;
  }

  public void dispose() {
    myModule = null;
    myLanguageLevel = null;
  }
}