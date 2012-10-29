package pkg;

public class ParameterNames {
  public void test(int index, int id, String name) {
    testImpl(index, id, name, true);
  }

  private void testImpl(int index, int id, String name, boolean print) {
    if (print) {
      System.out.println("[" + index + "] " + id + ":" + name);
    }
  }
}