/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.lang.Language;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author peter
 */
@State(
    name = "TemplateDataLanguagePatterns",
    storages = @Storage(id="default", file = "$OPTIONS$/templateLanguages.xml") )
public class TemplateDataLanguagePatterns implements PersistentStateComponent<Element> {
  private FileTypeAssocTable<Language> myAssocTable = new FileTypeAssocTable<Language>();
  @NonNls private static final String SEPARATOR = ";";

  public static TemplateDataLanguagePatterns getInstance() {
    return ServiceManager.getService(TemplateDataLanguagePatterns.class);
  }

  public FileTypeAssocTable<Language> getAssocTable() {
    return myAssocTable.copy();
  }

  @Nullable
  public Language getTemplateDataLanguageByFileName(VirtualFile file) {
    return myAssocTable.findAssociatedFileType(file.getName());
  }

  public void setAssocTable(FileTypeAssocTable<Language> assocTable) {
    myAssocTable = assocTable.copy();
  }

  public void loadState(Element state) {
    myAssocTable = new FileTypeAssocTable<Language>();

    final THashMap<String, Language> dialectMap = new THashMap<String, Language>();
    for (Language dialect : TemplateDataLanguageMappings.getTemplateableLanguages()) {
      dialectMap.put(dialect.getID(), dialect);
    }
    final List<Element> files = state.getChildren("pattern");
    for (Element fileElement : files) {
      final String patterns = fileElement.getAttributeValue("value");
      final String langId = fileElement.getAttributeValue("lang");
      final Language dialect = dialectMap.get(langId);
      if (dialect == null || StringUtil.isEmpty(patterns)) continue;

      for (String pattern : patterns.split(SEPARATOR)) {
        myAssocTable.addAssociation(FileTypeManager.parseFromString(pattern), dialect);
      }

    }
  }

  public Element getState() {
    Element state = new Element("x");
    for (final Language language : TemplateDataLanguageMappings.getTemplateableLanguages()) {
      final List<FileNameMatcher> matchers = myAssocTable.getAssociations(language);
      if (!matchers.isEmpty()) {
        final Element child = new Element("pattern");
        state.addContent(child);
        child.setAttribute("value", StringUtil.join(matchers, new Function<FileNameMatcher, String>() {
          public String fun(FileNameMatcher fileNameMatcher) {
            return fileNameMatcher.getPresentableString();
          }
        }, SEPARATOR));
        child.setAttribute("lang", language.getID());
      }
    }
    return state;
  }

}