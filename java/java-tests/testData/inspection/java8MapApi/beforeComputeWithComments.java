// "Replace with 'compute' method call" "true"
import java.util.Map;

public class Main {
  public void testCompute(Map<String, Integer> map, String key) {
    Integer value = map/*1*/./*2*/get/*3*/(/*4*/key/*5*/)/*6*/;
    map/*7*/./*8*/pu<caret>t/*9*/(/*10*/key/*11*/, value /*12*/== /*13*/null ? 0 : /*14*/value + 1)/*15*/;
  }
}