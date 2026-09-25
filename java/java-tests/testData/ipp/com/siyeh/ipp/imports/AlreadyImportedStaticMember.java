import static java.util.Arrays.sort;
import static java.util.Arrays.<caret>*;
class A {
  void f(String[] array){
    sort(array);
    System.out.println(asList(array));
  }
}