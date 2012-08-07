import java.util.*;
class Test {
  {
    Map<Number, String> map1 = null;
    Map<Integer, String> map2 = <error descr="Inconvertible types; cannot cast 'java.util.Map<java.lang.Number,java.lang.String>' to 'java.util.Map<java.lang.Integer,java.lang.String>'">(Map<Integer, String>) map1</error>;
  }
}