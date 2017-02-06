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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;

/**
 * A generic schemes model used in schemes UI: schemes combo box and related actions.
 *
 * @param <T> Actual scheme type derived from {@code Scheme}
 * @see AbstractSchemesPanel
 * @see AbstractSchemeActions
 */
public interface SchemesModel<T extends Scheme> {

  /**
   * @param scheme The scheme to check.
   * @return True if the scheme can be duplicated with a different name.
   */
  boolean canDuplicateScheme(@NotNull T scheme );

  /**
   * @param scheme The scheme to check.
   * @return True if the scheme can be reset from default values (presets) in principle regardless of whether scheme settings actually
   *         differ from presets or not.
   * @see #differsFromDefault(Scheme)
   */
  boolean canResetScheme(@NotNull T scheme);

  /**
   * @param scheme The scheme to check.
   * @return True if the scheme can be deleted, normally applies to custom schemes created by a user.
   */
  boolean canDeleteScheme(@NotNull T scheme);

  /**
   * @param scheme The scheme to check.
   * @return True if the given scheme is a project one. Always {@code false} if project schemes are not supported.
   */
  boolean isProjectScheme(@NotNull T scheme);

  /**
   * @param scheme The scheme to check.
   * @return True if scheme's name can be edited.
   */
  boolean canRenameScheme(@NotNull T scheme);

  /**
   * @param name The scheme to check.
   * @return True if a scheme by the given name already exists.
   */
  boolean containsScheme(@NotNull String name);

  /**
   * @param scheme The scheme to check.
   * @return True if scheme's settings differ from default values (presets). The method is called only if {@link #canResetScheme(Scheme)}
   *         returns {@code true}.
   */
  boolean differsFromDefault(@NotNull T scheme);
}
