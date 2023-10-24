// "Replace with 'compute' method call" "true"
import java.util.Map;

public class Main {
  public void testCompute(Map<String, Integer> map, String key) {
      map/*7*/./*8*/compute/*9*/(/*10*/key/*11*/, /*1*/ /*2*/ /*3*/ /*4*/ /*5*/ /*6*/ (k, value) -> value /*12*/ == /*13*/null ? 0 : /*14*/value + 1)/*15*/;
  }
}