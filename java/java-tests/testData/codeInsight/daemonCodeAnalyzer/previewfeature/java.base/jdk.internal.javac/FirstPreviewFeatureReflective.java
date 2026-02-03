package com.mycom;

@jdk.internal.javac.PreviewFeature(feature = jdk.internal.javac.PreviewFeature.Feature.SEALED_CLASSES, reflective = true)
public interface FirstPreviewFeatureReflective {
  public static final class Outer {
    public static final class Inner {
      public void z() {}
    }
  }
  void f();
  static void g() {}
  public static final String KEY = "value";
}