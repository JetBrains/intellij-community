package foo;

import static foo.MyEnum.*;
import static foo.MyEnumX.*;

public class Foo {
  void method(MyEnum e) { }
  void methodX(MyEnumX e) { }

  {
    method(cons<caret>)
  }


}

enum MyEnum { const1, const2 }
enum MyEnumX { constx1, constx2 }