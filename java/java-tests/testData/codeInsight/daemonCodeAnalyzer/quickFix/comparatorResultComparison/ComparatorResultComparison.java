import java.util.*;

class Test {
  void test(Comparator<String> cmp) {
    if(cmp.compare("a", "b") <warning descr="Comparison of compare method result with specific constant">!=</warning> -1) {
      System.out.println("Oops");
    }

    if(cmp.compare("a", "b") <warning descr="Comparison of compare method result with specific constant">></warning> 1) {
      System.out.println("Bad");
    }
    if(cmp.compare("a", "b") >= 1) {
      System.out.println("Ok");
    }
    if(cmp.compare("a", "b") < 1) {
      System.out.println("Ok");
    }
  }
}