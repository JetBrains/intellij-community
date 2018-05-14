// "Replace with primitive stream" "true"
import java.util.*;

public class Test {
  public void test() {
    List<Integer> ints = new ArrayList<>();
    ints/*1*/./*2*/stream/*3*/(/*4*/)<caret>/*5*/./*6*/mapToInt/*7*/(x ->/*8*/x)/*9*/./*10*/toArray();
  }
}