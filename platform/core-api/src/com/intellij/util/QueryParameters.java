// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * An interface to be (optionally, but preferably) implemented by {@link QueryExecutor} parameters to provide additional data.
 *
 * @author peter
 * @since 181.*
 */
public interface QueryParameters {

  @Nullable
  default Project getProject() {
    return null;
  }

  /**
   * @return whether this query still makes sense (e.g. PSI inside is still valid)
   */
  default boolean isQueryValid() {
    return true;
  }

  /**
   * Given:
   * <pre>
   *   class JavaClass {
   *     int getFoo() { return 42; }
   *   }
   *
   *   class GroovyClass {
   *     def usageInGroovy(JavaClass jc) {
   *       jc.foo       // resolves to special element which navigates to JavaClass.getFoo()
   *       jc.getFoo()  // resolves to JavaClass.getFoo() directly
   *     }
   *   }
   * </pre>
   * If this method returns {@code true} then only {@code jc.getFoo()} will be found.<br/>
   * All reference were found since ages, the default value is {@code false} to preserve this behaviour.
   *
   * @return {@code true} when only direct hits are expected
   */
  default boolean isDirectSearch() { return false; }
}
