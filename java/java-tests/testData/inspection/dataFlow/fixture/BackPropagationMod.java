import java.util.*;

public class BackPropagationMod {
  void testModSeries(int x) {
    if(x % 2 == 0) {}
    else if(<warning descr="Condition 'x % 2 != 0' is always 'true'">x % 2 != 0</warning>) {}

    if(x >= 0) {
      if(x % 3 == 0) {}
      else if(x % 3 == 1) {}
      else if(<warning descr="Condition 'x % 3 == 2' is always 'true'">x % 3 == 2</warning>) {}
    }
    if(x % 2 == 0) {}
    else if(x % 2 == 1) {}
    else if(<warning descr="Condition 'x % 2 == -1' is always 'true'">x % 2 == -1</warning>) {}
  }
  
  void testGt(int[] arr) {
    if (arr.length % 5 >= 2) {
      
    } else if(arr.length % 5 == 1) {
      
    } else if(<warning descr="Condition 'arr.length % 5 == 0' is always 'true'">arr.length % 5 == 0</warning>) {
      
    }
  }
  
  void testWrongFizzBuzz() {
    for (int i = 1; i <= 100; i++) {
      if (i % 3 == 0) {
        System.out.println("Fizz");
      } else if(i % 5 == 0) {
        System.out.println("Buzz");
      } else if(<warning descr="Condition 'i % 15 == 0' is always 'false'">i % 15 == 0</warning>) {
        System.out.println("FizzBuzz");
      } else {
        System.out.println(i);
      }
    }
  }

  void testSign(int x) {
    if (<warning descr="Condition 'x % 2 == 1 && x < 0' is always 'false'">x % 2 == 1 && <warning descr="Condition 'x < 0' is always 'false' when reached">x < 0</warning></warning>) {}
    if (<warning descr="Condition 'x % 2 == -1 && x > 0' is always 'false'">x % 2 == -1 && <warning descr="Condition 'x > 0' is always 'false' when reached">x > 0</warning></warning>) {}
    if (x % 2 > -1 && x < 0) {}
    if (x % 2 > -1 && x > 0) {}
  }
  
  // IDEABKL-6662
  void testMod510(StringBuilder sb, int pos) {
    if (pos % 5 == 0) {
      sb.append('.');
    }
    else if (<warning descr="Condition 'pos % 10 == 0' is always 'false'">pos % 10 == 0</warning>) {

    }
  }
}
