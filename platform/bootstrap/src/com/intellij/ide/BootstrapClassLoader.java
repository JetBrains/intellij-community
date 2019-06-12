// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BootstrapClassLoader extends UrlClassLoader {

  public interface Transformer {
    byte[] transform(ClassLoader loader, String className, @Nullable ProtectionDomain protectionDomain, byte[] classBytes);
  }

  private final List<Transformer> myTransformers = new CopyOnWriteArrayList<>();

  public BootstrapClassLoader(@NotNull Builder<BootstrapClassLoader> builder) {
    super(builder);
  }

  public void addTransformer(Transformer transformer) {
    myTransformers.add(transformer);
  }

  public boolean removeTransformer(Transformer transformer) {
    return myTransformers.remove(transformer);
  }

  @Override
  protected Class _defineClass(String name, byte[] b) {
    return super._defineClass(name, doTransform(name, null, b));
  }

  @Override
  protected Class _defineClass(String name, byte[] b, @Nullable ProtectionDomain protectionDomain) {
    return super._defineClass(name, doTransform(name, protectionDomain, b), protectionDomain);
  }

  private byte[] doTransform(String name, ProtectionDomain protectionDomain, byte[] bytes) {
    byte[] b = bytes;
    for (Transformer transformer : myTransformers) {
      final byte[] result = transformer.transform(this, name, protectionDomain, b);
      if (result != null) {
        b = result;
      }
    }
    return b;
  }
}
