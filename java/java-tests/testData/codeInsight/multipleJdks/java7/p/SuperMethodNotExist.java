package p;
public abstract class B extends A {
  public String getOrDefault(Object key, String defaultValue) {
    return super.<error descr="Cannot resolve method 'getOrDefault(java.lang.Object, java.lang.String)'">getOrDefault</error>(key, defaultValue);
  }
}