// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface was designed for getting portable caches for JPS, which uses to do an incremental compilation of the
 * project (e.g to have an opportunity to use caches from one build agent on another). To provide the support of this we
 * should add an opportunity to convert absolute paths, which cache contains, into the relative paths, so the not depends
 * on project location on filesystem or the location of the JDK for example.
 * JPS caches contains paths to the sources and all its dependencies e.g path to JDK libs, maven jars, build directory, etc.
 * In order to support this conversion we created several relativizers each of them supports conversion of the certain
 * types of paths {@link ProjectPathRelativizer} {@link JavaSdkPathRelativizer} {@link MavenPathRelativizer}. To leave
 * the opportunity to work with caches with relative paths aboard we should also provide a method to convert relative
 * paths back and get the paths to the files on the filesystem itself.
 *
 * <p>If you need to add the support of additional relativizer e.g Gradle dependencies path, you should implement this
 * interface and add the instance of class to the {@link PathRelativizerService#myRelativizers} list. For now it's not
 * supported to add the relativizers at the runtime because this feature is experimental for now.</p>
 *
 * <p><b>NOTE: It's preferred to pass the path to the relativizer before any actions on the path e.g. conversion to the
 * filesystem independent path</b></p>
 */
public interface PathRelativizer {
  /**
   * Convert concrete path to the relative. Returns {@code null} in specified path can't be converted to the relative
   * by this relativizer.
   *
   * <p><b>NOTE: It's preferred to pass the path to the relativizer before any actions on it e.g. conversion to the
   * filesystem independent path</b></p>
   */
  @Nullable
  String toRelativePath(@NotNull String path);

  /**
   * Convert concrete path to the absolute. Returns {@code null} in specified path can't be converted to the absolute
   * by this relativizer
   *
   * <p><b>NOTE: It's preferred to pass the path to the relativizer before any actions on it e.g. conversion to the
   * filesystem dependent path</b></p>
   */
  @Nullable
  String toAbsolutePath(@NotNull String path);
}
