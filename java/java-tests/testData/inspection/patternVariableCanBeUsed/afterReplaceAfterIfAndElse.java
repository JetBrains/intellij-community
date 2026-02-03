// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static Object foo5(Object o) {
    return new Runnable() {
      @Override
      public void run() {
        if (!(o instanceof Integer integer)) {
          return;
        }
        final String string = integer.toString();
        if (string.equals("1")) {
          return;
        }
        if (integer.intValue() == 1) {
          return;
        }

        print(integer);
      }
    };
  }


}