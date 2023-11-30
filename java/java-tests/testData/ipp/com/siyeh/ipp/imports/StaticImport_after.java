import static java.util.Arrays.asList;
import static java.util.Arrays.sort;

class A {
  void f(String[] array){
    sort(array);
    System.out.println(asList(array));
  }
}