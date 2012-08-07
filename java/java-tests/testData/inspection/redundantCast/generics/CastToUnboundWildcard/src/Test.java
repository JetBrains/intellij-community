import java.util.*;
import java.util.Map;

class Test {
  {
    Map<Number, String> map1 = null;
    Map<Integer, String> map2 = (Map<Integer, String>) (Map<?, ?>) map1;
  }
}