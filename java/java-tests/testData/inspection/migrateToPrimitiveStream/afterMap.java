// "Replace with primitive stream" "true"
import java.util.*;

public class Test {
  public void test() {
    List<Integer> ints = new ArrayList<>();
    ints/*1*/./*2*/stream/*3*/(/*4*/).mapToInt(v -> v)/*5*/./*6*/mapToObj/*7*/(x ->/*8*/new Object())/*9*/./*10*/toArray();
  }
}