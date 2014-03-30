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
      if (field == null) {
        System.out.println();
      }
    }
  }
}
