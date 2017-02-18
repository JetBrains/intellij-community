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
    construct1(String.class, createPath<error descr="'createPath(TestIDEA128101.Attribute<Y>)' in 'TestIDEA128101' cannot be applied to '(TestIDEA128101.Attribute<java.lang.Integer>)'">(integerAttribute)</error>, createPath(stringAttribute));
    construct2(String.class, createPath(integerAttribute), createPath(stringAttribute));
    construct3(String.class, createPath<error descr="'createPath(TestIDEA128101.Attribute<Y>)' in 'TestIDEA128101' cannot be applied to '(TestIDEA128101.Attribute<java.lang.Integer>)'">(integerAttribute)</error>, createPath<error descr="'createPath(TestIDEA128101.Attribute<Y>)' in 'TestIDEA128101' cannot be applied to '(TestIDEA128101.Attribute<java.lang.String>)'">(stringAttribute)</error>);
    construct4(String.class, createPath<error descr="'createPath(TestIDEA128101.Attribute<Y>)' in 'TestIDEA128101' cannot be applied to '(TestIDEA128101.Attribute<java.lang.Integer>)'">(integerAttribute)</error>, createPath<error descr="'createPath(TestIDEA128101.Attribute<Y>)' in 'TestIDEA128101' cannot be applied to '(TestIDEA128101.Attribute<java.lang.String>)'">(stringAttribute)</error>);
  }

}
