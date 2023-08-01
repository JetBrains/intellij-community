class QuickFix {
  private static Object INSTANCE;

  public static Object getInstance() {
    <warning descr="Double-checked locking"><caret>if</warning> (INSTANCE == null) {
      synchronized (QuickFix.class) {
        if (INSTANCE == null) {
          INSTANCE = new Object();
        }
      }
    }
    return INSTANCE;
  }
}