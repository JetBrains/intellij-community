// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package externalApp;

import org.jetbrains.annotations.ApiStatus;

/**
 * Interface for external CLI entry point.
 */
@ApiStatus.Experimental
public interface ExternalCli {
  /**
   * Called when some external process calls a dedicated CLI entry point of an IDE serving some specific task
   * For example, when IDE serves as GIT_EDITOR for interactive rebase, the git process starts the entry point, passing some parameters,
   * and this call is forwarded here, with all the context in the [entry] parameter.
   *
   * @return exit code which to finish the process with.
   */
  int entryPoint(ExternalAppEntry entry);
}
