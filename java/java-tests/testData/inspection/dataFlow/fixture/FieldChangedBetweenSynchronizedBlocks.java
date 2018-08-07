class Bar {
  private Object field;
  private final Object lock = new Object();

  public void main() {
    synchronized (lock) {
      if (field != null) {
        return;
      }
    }
    synchronized (lock) {
      if (field != null) {
        return;
      }
      if (<warning descr="Condition 'field == null' is always 'true'">field == null</warning>) {
        System.out.println();
      }
    }
  }
}
