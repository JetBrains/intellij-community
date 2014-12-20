import lombok.val;

import java.util.*;

class Test {
  <error descr="Incompatible types. Found: 'int', required: 'lombok.val'">val field = 0;</error>

  void method(val param) {
    <error descr="Incompatible types. Found: 'lombok.val', required: 'int'">int p = param;</error>

    val i = 0;
    int j = i + 1;

    val a = new ArrayList<String>();
    Object o = a.get(0);

    val b = new ArrayList<>();
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">String s = b.get(0);</error>
    o = b.get(0);

    for (val v : a) {
      String vStr = v;
    }
  }
}