// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.stubs.StubElement;

public interface PsiJavaModuleStub extends StubElement<PsiJavaModule> {
  /**
   * The module is not resolved by default from the class path.
   */
  int DO_NOT_RESOLVE_BY_DEFAULT = 1;

  /**
   * The module is marked as deprecated.
   */
  int WARN_DEPRECATED = 2;

  /**
   * The module is deprecated and will be removed in a future release.
   */
  int WARN_DEPRECATED_FOR_REMOVAL = 4;

  /**
   * The module is in incubating mode and not yet standardized.
   */
  int WARN_INCUBATING = 8;

  /**
   * Returns the name of the Java module.
   * <p>
   * This corresponds to the module declaration in {@code module-info.java} and is used in the
   * Java Platform Module System (JPMS).
   * </p>
   *
   * @see <a href="https://openjdk.org/jeps/261">JEP 261: Module System</a>
   * @return the module name
   */
  String getName();

  /**
   * Represents the attributes of a {@code module-info.class} file.
   * These attributes define specific behaviors related to module resolution and warnings.
   * <p>
   * See <a href="https://openjdk.org/jeps/11#Relationship-to-other-modules">JEP 11: Incubator Modules</a>
   * for more details.
   * </p>
   *
   * <ul>
   *   <li>{@code DO_NOT_RESOLVE_BY_DEFAULT} (0x0001) - Indicates that the module should not be resolved by default.</li>
   *   <li>{@code WARN_DEPRECATED} (0x0002) - Triggers a warning when a deprecated module is used.</li>
   *   <li>{@code WARN_DEPRECATED_REMOVAL} (0x0004) - Triggers a warning when a module is marked for future removal.</li>
   *   <li>{@code WARN_INCUBATING} (0x0008) - Triggers a warning when an incubating module is used.</li>
   * </ul>
   * <p>
   * If no attributes are set, the value is {@code 0}.
   * </p>
   *
   * @return a bitmask representing the module attributes, or {@code 0} if none are set.
   */
  int getResolution();
}