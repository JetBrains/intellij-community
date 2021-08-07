// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;


public interface ReferenceImporter {
  ExtensionPointName<ReferenceImporter> EP_NAME = ExtensionPointName.create("com.intellij.referenceImporter");

  boolean autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file);

  /**
   * Checks whether the IDE should try to add imports for unresolved references automatically.
   * Override in your language plugin when it provided adding new imports for unresolved references.
   * (like, for example, Java supports adding "import java.util.ArrayList" for unresolved reference "ArrayList list = null;")
   * @return true if the {@code file} is of type your plugin supports (i.e. {@link #autoImportReferenceAtCursor} will work for this file)
   * and the "add imports on the fly" feature is enabled for this file (e.g. "Settings|Auto Imports|Java|Add imports on the fly" option is on for the Java files).
   */
  default boolean isAddUnambiguousImportsOnTheFlyEnabled(@NotNull PsiFile file) {
    return false;
  }
}
