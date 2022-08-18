// "Unwrap" "true-preview"
import java.util.Arrays;
import java.util.List;

class Foo {
  interface Parent1 {}
  interface Parent2 {}
  interface Child1 extends Parent1, Parent2 {}
  interface Child2 extends Parent1, Parent2 {}

  void bar(boolean flag, Child1[] arr1, Child2[] arr2) {
    Parent2[] list = flag ? arr1 : arr2;
    for (Parent2 parent2 : list) {
      System.out.println(parent2);
    }
  }
}