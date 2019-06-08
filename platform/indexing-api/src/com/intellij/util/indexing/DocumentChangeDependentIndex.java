// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marker interface of index that use document changes to update it's data. These indices shouldn't depend on PSI-related stuff.
 *
 * Note, every {@link FileBasedIndexExtension} where {@link FileBasedIndexExtension#dependsOnFileContent()} returns false is treated as document change dependent.
 */
@ApiStatus.Experimental
public interface DocumentChangeDependentIndex {

}
