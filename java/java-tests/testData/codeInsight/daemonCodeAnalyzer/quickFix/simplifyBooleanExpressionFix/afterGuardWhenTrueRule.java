// "Remove guard expression" "true-preview"
class X {
  private static void test2(Object o) {
    switch (o) {
      case String t:
        System.out.println(1);
        break;
      case Object o1:
        System.out.println(1);
        break;
    }
  }

}