package com.mycom;

@jdk.internal.javac.PreviewFeature(feature = jdk.internal.javac.PreviewFeature.Feature.SEALED_CLASSES)
public interface FirstPreviewFeature {
  public static final class Outer {
    public static final class Inner {
      public void z() {}
    }
  }
  void f();
  static void g() {}
  public static final String KEY = "value";
}