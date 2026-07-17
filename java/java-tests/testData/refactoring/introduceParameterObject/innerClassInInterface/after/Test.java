public interface Test {
  void foo(Param param);

    record Param(String s) {
    }
}

class TestImpl implements Test {
  void foo(Param param){}
}

