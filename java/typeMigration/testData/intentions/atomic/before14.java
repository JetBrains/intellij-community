// "Convert to atomic" "true"
class T {
  private long <caret>l = 10L;

  public synchronized void update(long m) {
    l = m;
  }
}