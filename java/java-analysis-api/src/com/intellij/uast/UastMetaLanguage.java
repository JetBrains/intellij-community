/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.uast;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.util.containers.HashSet;
import org.jetbrains.uast.UastLanguagePlugin;

import java.util.Collection;
import java.util.Set;

/**
 * @author yole
 */
public class UastMetaLanguage extends MetaLanguage {
  private final Set<Language> myLanguages = new HashSet<>();

  protected UastMetaLanguage() {
    super("UAST");
    for (UastLanguagePlugin plugin: UastLanguagePlugin.Companion.getInstances()) {
      myLanguages.add(plugin.getLanguage());
    }
  }

  @Override
  public boolean matchesLanguage(Language language) {
    return myLanguages.contains(language);
  }

  @Override
  public Collection<Language> getMatchingLanguages() {
    return myLanguages;
  }
}
