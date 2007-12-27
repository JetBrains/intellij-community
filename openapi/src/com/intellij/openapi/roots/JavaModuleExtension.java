/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class JavaModuleExtension extends LanguageModuleExtension<JavaModuleExtension> {
  @NonNls private static final String LANGUAGE_LEVEL_ELEMENT_NAME = "LANGUAGE_LEVEL";
  private final Module myModule;

  public static JavaModuleExtension getInstance(final Module module) {
    final LanguageModuleExtension[] extensions = Extensions.getExtensions(EP_NAME, module);
    for (LanguageModuleExtension extension : extensions) {
      if (extension.getClass().isAssignableFrom(JavaModuleExtension.class)) return (JavaModuleExtension)extension;
    }
    return null;
  }

  private LanguageLevel myLanguageLevel;

  public JavaModuleExtension(Module module) {
    myModule = module;
  }

  public void setLanguageLevel(final LanguageLevel languageLevel) {
    if (myModule.isLoaded()) { //do not reload project for non-commited modules: j2me|project imports
      if (myLanguageLevel != languageLevel) {
        final JavaProjectExtension javaProjectExtension = JavaProjectExtension.getInstance(myModule.getProject());
        final LanguageLevel projectLanguageLevel = javaProjectExtension.getLanguageLevel();
        if (!(languageLevel == null && myLanguageLevel == projectLanguageLevel || 
             (myLanguageLevel == null && languageLevel == projectLanguageLevel))) {
          javaProjectExtension.reloadProjectOnLanguageLevelChange(languageLevel, true);
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

  public void copy(final JavaModuleExtension source) {
    myLanguageLevel = source.myLanguageLevel;
  }

  public boolean isModified(final JavaModuleExtension source) {
    return myLanguageLevel != source.myLanguageLevel;
  }
}