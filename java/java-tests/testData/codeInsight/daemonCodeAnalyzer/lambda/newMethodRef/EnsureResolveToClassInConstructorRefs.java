import java.util.function.Supplier;

class TEST_NAME {
  TEST_NAME(){}
}
class TEST_NAME1 {}

class TestClass {
  static{
    String TEST_NAME = "";
    Supplier<TEST_NAME> s = TEST_NAME::new;

    String TEST_NAME1 = "";
    Supplier<TEST_NAME1> s1 = TEST_NAME1::new;
  }
}