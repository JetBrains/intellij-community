// "Surround with array initialization" "true"
import java.util.List;

class A {

  public void test(List<?>[] t){}

  void foo(List<Number> list) {
    test(new List[]{list});
  }
}