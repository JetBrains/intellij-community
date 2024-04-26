// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.lang.annotation.ContributedReferencesAnnotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NonNls;

public final class ContributedReferencesAnnotators extends LanguageExtension<ContributedReferencesAnnotator> {
  public static final @NonNls ExtensionPointName<KeyedLazyInstance<ContributedReferencesAnnotator>> EP_NAME =
    ExtensionPointName.create("com.intellij.contributedReferencesAnnotator");

  public static final ContributedReferencesAnnotators INSTANCE = new ContributedReferencesAnnotators();

  private ContributedReferencesAnnotators() {
    super(EP_NAME);
  }
}
