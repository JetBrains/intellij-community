class Test {
  static class ABC{
    public int i = 0;
  }
  static {
    System.out.println("" + getABC().<caret>i);
  }

  static ABC getABC(){
    return new ABC();
  }
}
