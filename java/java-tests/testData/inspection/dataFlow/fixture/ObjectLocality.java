import java.util.*;

import org.jetbrains.annotations.*;

class ObjectLocality {
  int x;

  void test() {
    ObjectLocality o = new ObjectLocality();
    o.x = 5;
    unknown();
    <warning descr="Variable is already assigned to this value">o.x</warning> = 5;
    o.unknown();
    o.x = 5;
  }

  native void unknown();
}
