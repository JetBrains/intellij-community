// "Remove unreachable branches" "true"
class Test {
    void test(R r) {
        switch (r) {
            case R(int i, String str, double ignored)<caret> -> {
                System.out.println(i);
                i = 42;
                System.out.println(str + i);
            }
            case R ignored when false -> {
            }
    }
  }

  record R(int i, String s, double ignored) {
  }
}