/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentHashMap;

import java.util.concurrent.ConcurrentMap;

/**
 * @author Medvedev Max
 */
public class PsiTopLevelElementFactories extends LanguageExtension<PsiTopLevelElementFactoryProvider> {
  private static final ConcurrentMap<Pair<Language, Project>, PsiTopLevelElementFactory> factories = new ConcurrentHashMap<Pair<Language, Project>, PsiTopLevelElementFactory>();

  private static final PsiTopLevelElementFactories INSTANCE = new PsiTopLevelElementFactories();

  private PsiTopLevelElementFactories() {
    super("com.intellij.generation.topLevelFactory");
  }

  public static PsiTopLevelElementFactory getFactory(Language language, Project project) {
    final Pair<Language, Project> key = Pair.create(language, project);
    final PsiTopLevelElementFactory factory = factories.get(key);
    if (factory != null) return factory;
    final PsiTopLevelElementFactoryProvider provider = INSTANCE.forLanguage(language);
    return ConcurrencyUtil.cacheOrGet(factories, key, provider.getFactory(project));
  }
}
