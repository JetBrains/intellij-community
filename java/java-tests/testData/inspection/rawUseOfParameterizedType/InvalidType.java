import java.util.ArrayList;

class TestNullType {
  void test() {
    <error descr="Cannot infer type: variable initializer is 'null'">var</error> x = null;
    x = new <warning descr="Raw use of parameterized class 'ArrayList'">ArrayList</warning>();
  }
}