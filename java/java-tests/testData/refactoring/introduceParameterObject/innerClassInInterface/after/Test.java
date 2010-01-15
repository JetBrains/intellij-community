public interface Test {
  void foo(Param param);

    private static class Param {
        private final String s;

        private Param(String s) {
            this.s = s;
        }

        public String getS() {
            return s;
        }
    }
}

class TestImpl implements Test {
  void foo(Test.Param param){}
}

