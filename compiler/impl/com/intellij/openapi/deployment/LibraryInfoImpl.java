/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.deployment;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.InvalidDataException;
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
  public Library findLibrary(final Module module, final @Nullable ModulesProvider provider) {
    if (module == null) return null;

    if (LibraryLink.MODULE_LEVEL.equals(myLevel)) {
      String url = myUrls.size() == 1 ? myUrls.get(0) : null;
      return LibraryLinkUtil.findModuleLibrary(module, provider, myName, url);
    }
    return LibraryLink.findLibrary(myName, myLevel, module.getProject());
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
