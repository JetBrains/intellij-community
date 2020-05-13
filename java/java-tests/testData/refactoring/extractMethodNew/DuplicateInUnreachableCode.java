
class A {
  private static final boolean ourOverrideFinalFields = false;

  public static String createShared(char[] chars) {

    if (ourOverrideFinalFields) {
      String s = new String();
      return s;
    }
    String s = <selection>new String()</selection>;
    return new String(chars);
  }


}