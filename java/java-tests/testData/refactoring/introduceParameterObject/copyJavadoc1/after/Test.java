class Test {
  /**
   * foo comment
   * @param param
   * @param s1 long1 description1
   */
  void foo(Param param, String s1) {
    bar(param.getS(), s1);
  }

  void bar(String s, String s1){}

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
