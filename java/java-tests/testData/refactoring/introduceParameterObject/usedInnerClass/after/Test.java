class Test {
  void foo(Param param) {
    bar(param.getS());
  }

  void bar(String s){}

    static class Param {
        private final String s;

        Param(String s) {
            this.s = s;
        }

        public String getS() {
            return s;
        }
    }
}