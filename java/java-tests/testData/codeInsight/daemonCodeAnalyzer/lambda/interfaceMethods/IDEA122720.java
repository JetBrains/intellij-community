interface I {
  default void f() {}
}

class P {
  public void f() {}
}

class AP extends P implements I {
  @Override
  public void f() {
    I.super.f();
  }
}

class AC implements Cloneable {

  public Object clone() throws CloneNotSupportedException {
    return Cloneable.super.<error descr="'clone()' has protected access in 'java.lang.Object'">clone</error>();
  }
}