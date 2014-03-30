// "Change parameter 'i' type to 'long'" "false"

class M extends Thread {
  @Override
  public boolean equals(Object obj) {
    Thread.sleep(<caret>obj);
    return super.equals(obj);
  }
}