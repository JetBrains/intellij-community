public interface Test {
  void foo(Param param);

    public static class Param {
        private final String s;

        public Param(String s) {
            this.s = s;
        }

        public String getS() {
            return s;
        }
    }
}

class TestImpl implements Test {
  void foo(Param param){}
}

