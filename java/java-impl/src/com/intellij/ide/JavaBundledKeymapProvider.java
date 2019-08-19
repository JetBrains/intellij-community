// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.keymap.impl.BundledKeymapProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author gregsh
 */
public class JavaBundledKeymapProvider implements BundledKeymapProvider {
  @NotNull
  @Override
  public List<String> getKeymapFileNames() {
    return Arrays.asList(
      "Visual Studio.xml",
      "Eclipse.xml",
      "Eclipse (Mac OS X).xml",
      "NetBeans 6.5.xml"
    );
  }
}
