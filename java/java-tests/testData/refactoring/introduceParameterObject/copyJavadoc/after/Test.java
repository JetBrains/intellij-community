class Test {
  /**
   * foo comment
   * @param param
   */
  void foo(Param param) {
    bar(param.getS());
  }

  void bar(String s){}

    private static class Param {
        private final String s;

        /**
         * @param s long description
         */
        private Param(String s) {
            this.s = s;
        }

        public String getS() {
            return s;
        }
    }
}
