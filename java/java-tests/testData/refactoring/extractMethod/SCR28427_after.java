class TestCase {
  boolean a = true;
  boolean b = false;
  boolean x = newMethod();

    private boolean newMethod() {
        return a && b;
    }
}