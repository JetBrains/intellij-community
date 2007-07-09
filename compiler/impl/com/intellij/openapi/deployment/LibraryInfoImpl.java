/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.deployment;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
*/
class LibraryInfoImpl implements LibraryInfo {
  private final List<String> myUrls = new ArrayList<String>();
  private String myName;
  private String myLevel;

  public String getName() {
    return myName;
  }

  @NotNull
  public List<String> getUrls() {
    return myUrls;
  }

  public String getLevel() {
    return myLevel;
  }

  public void addUrl(String url) {
    myUrls.clear();
    myUrls.add(url);
  }

  @Nullable
  public Library findLibrary(final Project project, final Module module, final @Nullable ModulesProvider provider) {
    if (LibraryLink.MODULE_LEVEL.equals(myLevel)) {
      String url = myUrls.size() == 1 ? myUrls.get(0) : null;
      return module == null? null : LibraryLinkUtil.findModuleLibrary(module, provider, myName, url);
    }
    return LibraryLink.findLibrary(myName, myLevel, project);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myName = element.getAttributeValue(LibraryLinkImpl.NAME_ATTRIBUTE_NAME);

    myLevel = element.getAttributeValue(LibraryLinkImpl.LEVEL_ATTRIBUTE_NAME);
    myUrls.clear();
    final List urls = element.getChildren(LibraryLinkImpl.URL_ELEMENT_NAME);
    for (Object url1 : urls) {
      Element url = (Element)url1;
      myUrls.add(url.getText());
    }
  }
}
