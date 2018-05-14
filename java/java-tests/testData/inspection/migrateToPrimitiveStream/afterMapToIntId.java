// "Replace with primitive stream" "true"
import java.util.*;

public class Test {
  public void test() {
    List<Integer> ints = new ArrayList<>();
      /*5*/
      /*6*/
      /*7*/
      /*8*/
      ints/*1*/./*2*/stream/*3*/(/*4*/).mapToInt(v -> v)/*9*/./*10*/toArray();
  }
}