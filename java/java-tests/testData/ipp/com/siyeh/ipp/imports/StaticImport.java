import static java.util.Arrays.*;
class A {
  void f(String[] array){
    sort(array);
    System.out.println(asList(array));
  }
}