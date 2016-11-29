class A<T> {
  public void testMethod(T... values) {}
  public void testMethod1(T values) {}
}

class B extends A<Integer> {
  public static void testMethod(String... values) {}
  public static void testMethod1(String values) {}
}

class Test45 {
  public static void main(String[] args) {
    B.testMethod<error descr="Ambiguous method call: both 'B.testMethod(String...)' and 'A.testMethod(Integer...)' match">()</error>;
    B.testMethod1<error descr="Ambiguous method call: both 'B.testMethod1(String)' and 'A.testMethod1(Integer)' match">(null)</error>;
  }
}