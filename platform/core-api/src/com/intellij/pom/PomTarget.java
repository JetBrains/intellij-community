// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom;

import com.intellij.model.ModelElement;

/**
 * Accompanied by a PSI element via {@link com.intellij.pom.references.PomService#convertToPsi(PomTarget)}. All references to this target
 * should resolve to that PSI element.
 *
 * @author peter
 */
public interface PomTarget extends Navigatable, ModelElement {
  PomTarget[] EMPTY_ARRAY = new PomTarget[0];

  boolean isValid();
}
