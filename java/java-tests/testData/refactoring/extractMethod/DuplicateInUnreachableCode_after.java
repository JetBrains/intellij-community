import org.jetbrains.annotations.NotNull;

class A {
  private static final boolean ourOverrideFinalFields = false;

  public static String createShared(char[] chars) {

    if (ourOverrideFinalFields) {
      String s = newMethod();
      return s;
    }
    String s = newMethod();
    return new String(chars);
  }

    @NotNull
    private static String newMethod() {
        return new String();
    }


}