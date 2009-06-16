/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom;

/**
 * Accompanied by a PSI element via {@link com.intellij.pom.references.PomService#convertToPsi(PomTarget)}. All references to this target
 * should resolve to that PSI element.
 *
 * @author peter
 */
public interface PomTarget extends Navigatable {
  PomTarget[] EMPTY_ARRAY = new PomTarget[0];

  boolean isValid();
}
