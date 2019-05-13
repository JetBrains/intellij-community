package p;

abstract class B extends A {
  public static String getOrDefault(Object key, String defaultValue) {
    return null;
  }
}

abstract class C extends A {
  <error descr="Method does not override method from its superclass">@Override</error>
  public String getOrDefault(Object key, String defaultValue) {
    return null;
  }
}