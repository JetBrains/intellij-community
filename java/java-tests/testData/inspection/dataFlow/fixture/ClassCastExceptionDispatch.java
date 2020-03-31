class X {

  public static void main(final String[] args) {
    final Object obj = getObject();
    String str = null;
    try {
      str = (String) obj;
    } catch (final AssertionError ignored) {
      if (obj instanceof String) {}
      if (str != null) {}
    } catch (final ClassCastException ignored) {
      if (<warning descr="Condition 'obj instanceof String' is always 'false'">obj instanceof String</warning>) {}
      if (<warning descr="Condition 'obj == null' is always 'false'">obj == null</warning>) {}
      if (<warning descr="Condition 'str != null' is always 'false'">str != null</warning>) {}
    }
    if (str != null) {
      System.out.println("str = " + str);
    }
  }

  private static native Object getObject();

  void testDoubleCatch(Object obj) {
    try {
      System.out.println(((String)obj).length());
    }
    catch (Exception ex) {}
    try {
      System.out.println(((String)obj).trim());
    }
    catch (Exception ex) {}
  }

  void testFinally(Object obj) {
    try {
      System.out.println(((String)obj).length());
    }
    catch (Exception ex) {}
    finally {
      System.out.println(((String)obj).trim());
    }
  }
}