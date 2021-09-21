// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.impl;

import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.JBIterable;

import java.util.LinkedHashSet;

public final class LangFindSettingsImpl extends FindSettingsImpl {

  @Override
  public void noStateLoaded() {
    LinkedHashSet<String> extensions = JBIterable.from(IdeLanguageCustomization.getInstance().getPrimaryIdeLanguages())
      .filterMap(Language::getAssociatedFileType)
      .flatten(o -> JBIterable.of(o.getDefaultExtension())
        .append(JBIterable.from(FileTypeManager.getInstance().getAssociations(o))
                  .filter(ExtensionFileNameMatcher.class)
                  .filterMap(ExtensionFileNameMatcher::getExtension)))
      .addAllTo(new LinkedHashSet<>());
    if (extensions.contains("java")) {
      extensions.add("properties");
      extensions.add("jsp");
    }
    if (!extensions.contains("sql")) {
      extensions.add("xml");
      extensions.add("html");
      extensions.add("css");
    }

    String[] extensionsArray = ArrayUtil.toStringArray(extensions);
    for (int i = extensionsArray.length - 1; i >= 0; i--) {
      FindInProjectSettingsBase.addRecentStringToList("*." + extensionsArray[i], recentFileMasks);
    }
  }
}
