import java.util.List;

class MyClass {
  <error descr="'method1(Comparable<Integer>)' clashes with 'method1(Comparable<Boolean>)'; both methods have same erasure">void method1 (Comparable<Integer> c)</error> {}
  void method1(Comparable<Boolean> c) {}

  <error descr="'method2(List<Integer>)' clashes with 'method2(List<Boolean>)'; both methods have same erasure">void method2(List<Integer> l)</error> {} 
  void method2(List<Boolean> l) {}
}
