// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cmdline.PreloadedData;

/**
 * This extension allows a JPS plugin to move some activities to a JPS process preload phase.
 * Typical application is to pre-load and initialize some plugin custom caches to save time on build run.
 *
 * @author Eugene Zhuravlev
 */
public interface PreloadedDataExtension {

  /**
   * Called on preload phase. A jps plugin can prepare any information and store it in the passed PreloadedData object
   * @param data a data bean to store custom data. See {@link PreloadedData#putUserData} and {@link PreloadedData#getUserData}
   */
  void preloadData(@NotNull PreloadedData data);

  /**
   * Called before build starts on Buildsession initialization. At this point the project and project model are already created and data from the
   * PreloadedData bean can be used to initialized plugin's services or/and data structures.
   * @param data a data bean where custom data is stored. See {@link PreloadedData#putUserData} and {@link PreloadedData#getUserData}
   */
  void buildSessionInitialized(PreloadedData data);

  /**
   * Called if preloaded process is cancelled or if preloaded data should be disregarded.
   * At this poing plugin can gracefully dispose its preloaded data structures.
   * @param data a data bean where custom data is stored. See {@link PreloadedData#putUserData} and {@link PreloadedData#getUserData}
   */
  void discardPreloadedData(PreloadedData data);
}
