import lombok.val;

import java.util.*;

class Test {
  val field = <error descr="Incompatible types. Found: 'int', required: 'lombok.val'">0;</error>

  void method(val param) {
    int p = <error descr="Incompatible types. Found: 'lombok.val', required: 'int'">param</error>;

    val i = 0;
    int j = i + 1;

    val a = new ArrayList<String>();
    Object o = a.get(0);

    val b = new ArrayList<>();
    String s = b.<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">get</error>(0);
    o = b.get(0);

    for (val v : a) {
      String vStr = v;
    }

    val x = 0;
    <error descr="Cannot assign a value to final variable 'x'">x</error>++;
  }
}