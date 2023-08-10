public interface Test {
  void foo(Param param);

    private static record Param(String s) {
    }
}

class TestImpl implements Test {
  void foo(Test.Param param){}
}

