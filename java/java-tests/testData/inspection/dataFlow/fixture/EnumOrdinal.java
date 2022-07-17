public class EnumOrdinal {
  void test3(MyEnum e) {
    if (e.ordinal() == 1) {
      if (e == MyEnum.B) {}
    }
    if (e == MyEnum.C) {
      if (<warning descr="Condition 'e.ordinal() == 2' is always 'true'">e.ordinal() == 2</warning>) {}
    }
  }

  enum MyEnum {
    A, B {}, C, D
  }

  void test() {
    MyEnum e1 = MyEnum.A;
    MyEnum e2 = MyEnum.B;
    if (<warning descr="Condition 'e1.compareTo(e2) > 0' is always 'false'">e1.compareTo(e2) > 0</warning>) {}
  }

  void test2(MyEnum e) {
    if (e.compareTo(MyEnum.B) < 0) {
      if (e == MyEnum.A) {}
      if (<warning descr="Condition 'e == MyEnum.C' is always 'false'">e == MyEnum.C</warning>) {}
      if (<warning descr="Condition 'e.compareTo(MyEnum.B) > 0' is always 'false'">e.compareTo(MyEnum.B) > 0</warning>) {

      }
    }
  }

  void testAll(MyEnum e) {
    if (e == MyEnum.A) {}
    else if (e == MyEnum.B) {}
    else if (e == MyEnum.C) {}
    else if (e == MyEnum.D) {}
    else {}
  }
}