public class ATest {
  public static final String FOO = "foo";
  public static final String FOO1 = "foo1";

    String m_interval = "";
    void foo(String mInterval) {
      if (FOO.equals(mInterval.substring(1))) {
          System.out.println(FOO);
      } else if (FOO1.equals(mInterval.substring(1))) {
          System.out.println();
      }
    }
}
