class TestIDEA128101 {

  static class Attribute<Y> {};
  static class Path<X> {};

  static Attribute<Integer> integerAttribute;
  static Attribute<String> stringAttribute;

  static <Y> Path<Y> createPath(Attribute<Y> attribute) {
    return new Path<>();
  }
  static <Y> Path<Y> createPath1(Attribute<Y> attribute) {
    return new Path<>();
  }
  static <T> void construct(Class<T> aClass, Path<?>... paths) {}
  static <T, K> void construct1(Class<T> aClass, Path<K>... paths) {}
  static <T, K> void construct2(Class<T> aClass, Path<? extends K>... paths) {}
  static <T, K> void construct3(Class<T> aClass, Path<? super K>... paths) {}
  static <T, K> void construct4(Class<T> aClass, Path<? super K> path1, Path<? super K> path2) {}

  public static void test() {
    construct(String.class, createPath(integerAttribute), createPath(stringAttribute));
    construct1<error descr="'construct1(java.lang.Class<T>, TestIDEA128101.Path<K>...)' in 'TestIDEA128101' cannot be applied to '(java.lang.Class<java.lang.String>, TestIDEA128101.Path<Y>, TestIDEA128101.Path<java.lang.String>)'">(String.class, createPath(integerAttribute), createPath(stringAttribute))</error>;
    construct2(String.class, createPath(integerAttribute), createPath(stringAttribute));
    construct3<error descr="'construct3(java.lang.Class<java.lang.String>, TestIDEA128101.Path<? super K>...)' in 'TestIDEA128101' cannot be applied to '(java.lang.Class<java.lang.String>, TestIDEA128101.Path<Y>, TestIDEA128101.Path<Y>)'">(String.class, createPath(integerAttribute), createPath(stringAttribute))</error>;
    construct4<error descr="'construct4(java.lang.Class<java.lang.String>, TestIDEA128101.Path<? super K>, TestIDEA128101.Path<? super K>)' in 'TestIDEA128101' cannot be applied to '(java.lang.Class<java.lang.String>, TestIDEA128101.Path<Y>, TestIDEA128101.Path<Y>)'">(String.class, createPath(integerAttribute), createPath(stringAttribute))</error>;
  }

}
