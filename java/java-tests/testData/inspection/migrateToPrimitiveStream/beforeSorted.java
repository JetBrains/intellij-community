// "Replace with primitive stream" "true"
import java.util.*;

public class Test {
  public void test() {
    List<Integer> ints = new ArrayList<>();
    ints/*1*/./*2*/stream/*3*/(/*4*/)<caret>/*5*/./*6*/sorted/*7*/(/*8*/)/*9*/./*10*/toArray();
  }
}