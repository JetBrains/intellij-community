// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Medvedev Max
 */
public final class JVMElementFactories extends LanguageExtension<JVMElementFactoryProvider> {
  private static final JVMElementFactories INSTANCE = new JVMElementFactories();

  private JVMElementFactories() {
    super("com.intellij.generation.topLevelFactory");
  }

  @Nullable
  public static JVMElementFactory getFactory(@NotNull Language language, @NotNull Project project) {
    final JVMElementFactoryProvider provider = INSTANCE.forLanguage(language);
    return provider != null? provider.getFactory(project) : null;
  }

  @NotNull
  public static JVMElementFactory requireFactory(@NotNull Language language, @NotNull Project project) {
    final JVMElementFactory factory = getFactory(language, project);
    assert factory != null : language;
    return factory;
  }
}
