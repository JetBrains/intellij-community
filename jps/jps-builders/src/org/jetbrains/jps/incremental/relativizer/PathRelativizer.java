// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface was designed for getting portable caches required for incremental compilation of the
 * project (e.g. to have an opportunity to use caches from one build agent on another). To provide the support of this we
 * should add an opportunity to convert absolute paths, which cache contains, into the relative paths, so they don't depend
 * on project location on filesystem or the location of the JDK for example.
 * JPS caches contain paths to the sources and all its dependencies e.g. path to JDK libs, maven jars, build directory, etc.
 * In order to support this conversion we created several relativizers each of them supporting conversion of the certain
 * types of paths {@link CommonPathRelativizer}, {@link JavaSdkPathRelativizer}, {@link MavenPathRelativizer}. To leave
 * the opportunity to work with caches with relative paths aboard we should also provide a method to convert relative
 * paths back and get the paths to the files on the filesystem itself.
 *
 * <p>If you need to add the support of additional relativizer e.g Gradle dependencies path, you should implement this
 * interface and add the instance of class to the {@link PathRelativizerService#myRelativizers} list.
 * Adding the relativizers at the runtime it's not supported because this feature is experimental for now.</p>
 *
 * <p><b>NOTE: Relativizer works with system-independent paths. You shouldn't do any explicit conversion before passing
 * path to the methods if you are working via {@link PathRelativizerService}, all necessary things done in
 * {@link PathRelativizerService#toRelative} and {@link PathRelativizerService#toFull}</b></p>
 */
public interface PathRelativizer {
  /**
   * Convert concrete absolute path to the relative
   *
   * @param path absolute path to the file, path should be system-independent.
   *
   * @return system-independent relative path. Returns {@code null} if specified path can't be converted to
   * the relative by this relativizer.
   */
  @Nullable
  String toRelativePath(@NotNull String path);

  /**
   * Convert concrete relative path to the absolute
   *
   * @param path relative path to the file, path should be system-independent.
   *
   * @return system-independent absolute path. Returns {@code null} if specified path can't be converted to
   * the absolute by this relativizer.
   */
  @Nullable
  String toAbsolutePath(@NotNull String path);
}
