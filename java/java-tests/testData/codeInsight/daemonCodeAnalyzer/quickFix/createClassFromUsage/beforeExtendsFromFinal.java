// "Create class 'Abc'" "false"
public class Test {
  void foo(Class<? extends A> cl){}
  {
    foo(A<caret>bc.class);
  }
}
final class A {}