package com.siyeh.igtest.classlayout.public_constructor;

public class <warning descr="Class 'PublicConstructor' has default 'public' constructor">Public<caret>Constructor</warning> {}
abstract class X implements java.io.Externalizable {
  public X() {}
}
class Y {
  public <warning descr="Public constructor 'Y()'">Y</warning>() {}
}
abstract class Z {
  public Z() {}
}