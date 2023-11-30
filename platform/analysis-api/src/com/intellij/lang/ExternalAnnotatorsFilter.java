// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;

/**
 * Allows skipping running specific {@link ExternalAnnotator} for given file.
 *
 * @author Dmitry Avdeev
 */
public interface ExternalAnnotatorsFilter {

  ExtensionPointName<ExternalAnnotatorsFilter> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.daemon.externalAnnotatorsFilter");

  boolean isProhibited(ExternalAnnotator annotator, PsiFile file);
}
