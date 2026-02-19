// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static Object foo5(Object o) {
    return new Runnable() {
      @Override
      public void run() {
        if (!(o instanceof Integer)) {
          return;
        }
        final String string = ((Intege<caret>r) o).toString();
        if (string.equals("1")) {
          return;
        }
        if (((Integer) o).intValue() == 1) {
          return;
        }

        print(((Integer) o));
      }
    };
  }


}