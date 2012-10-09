// "Replace with method reference" "true"
class Test {
  interface Bar {
    int compare(String o1, String o2);
  }
    static int c(String o1, String o2) {
        return 0;
    }

  {
    Bar bar2 = new B<caret>ar() {
        @Override
        public int compare(final String o1, final String o2) {
            return c(o1, o2);
        }
    };
  }
}