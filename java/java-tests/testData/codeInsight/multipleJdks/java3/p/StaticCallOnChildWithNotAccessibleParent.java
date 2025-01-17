package p;

import static p.<error descr="Cannot access p.BaseClass">ChildClass</error>.*;

class Sample {
  public static void main(String[] args) {
    <error descr="Cannot access p.BaseClass">ChildClass.foo</error>();
    foo();
    ChildClass cc = ChildClass2.childClass();
  }
}
