// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.OutputStream;

@ApiStatus.Experimental
public abstract class AttributeOutputStream extends DataOutputStream {
  public AttributeOutputStream(OutputStream out) {
    super(out);
  }

  abstract public void writeEnumeratedString(String str) throws IOException;
}
