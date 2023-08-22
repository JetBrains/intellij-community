public class ThisInEnumSubclass {
  enum MyEnum {
    A, B, C {
      void test() {
        if (<warning descr="Condition 'this == C' is always 'true'">this == C</warning>) {}
      }
    }
  }
}