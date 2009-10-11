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

package com.intellij.openapi.deployment;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
*/
class LibraryInfoImpl implements LibraryInfo {
  private final List<String> myUrls = new ArrayList<String>();
  private String myName;
  private String myLevel;

  LibraryInfoImpl() {
  }

  LibraryInfoImpl(@NotNull Library library) {
    myName = library.getName();
    LibraryTable table = library.getTable();
    myLevel = table == null ? LibraryLink.MODULE_LEVEL : table.getTableLevel();
    myUrls.addAll(Arrays.asList(library.getUrls(OrderRootType.CLASSES)));
  }

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
