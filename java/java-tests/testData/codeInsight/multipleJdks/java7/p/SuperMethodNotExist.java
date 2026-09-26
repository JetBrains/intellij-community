package p;
public abstract class B extends A {
  public String getOrDefault(Object key, String defaultValue) {
    return super.<error descr="Cannot resolve method 'getOrDefault' in 'A'">getOrDefault</error>(key, defaultValue);
  }
}