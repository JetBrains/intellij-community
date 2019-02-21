// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public interface BuildOutputInstantReader extends Closeable, Appendable {
  Object getBuildId();

  @Nullable
  String readLine();

  void pushBack();

  /***
   * Push back the given number of lines.
   * @param numberOfLines
   */
  void pushBack(int numberOfLines);

  String getCurrentLine();

  /*
    redefine below methods without exceptions
   */

  @Override
  BuildOutputInstantReader append(CharSequence csq);

  @Override
  BuildOutputInstantReader append(CharSequence csq, int start, int end);

  @Override
  BuildOutputInstantReader append(char c);

  @Override
  void close();
}
