// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import com.intellij.build.FilePosition;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public interface FileMessageEvent extends MessageEvent {
  FilePosition getFilePosition();

  @Override
  FileMessageEventResult getResult();
}
