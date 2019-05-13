package p;

import static p.A.of;
import static p.B.of;

class Test {
  {
    of(1, 2, 3);
    of<error descr="Ambiguous method call: both 'A.of(Integer)' and 'B.of(Integer)' match">(4)</error>;
  }
}