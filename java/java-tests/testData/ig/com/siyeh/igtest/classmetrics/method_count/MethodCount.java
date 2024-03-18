package com.siyeh.igtest.classmetrics.method_count;

public class <warning descr="'MethodCount' has too many methods (method count = 6)">MethodCount</warning> {
  public void one() {}
  public void two() {}
  public void three() {}
  public void four() {}
  public void five() {}
  public void six() {}
}
class NotTooMany {
  public void one() {}
  public void two() {}
  public void three() {}
  public void four() {}
  public void five() {}
}
class SuperMethods {

  public void one() {}
  public void two() {}
  public void three() {}

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj);
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public String toString() {
    return super.toString();
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
  }
}
record Big(int one,  int two, int three, int four, int five, int six, int seven, int eight, int nine, int ten, int eleven) {}
