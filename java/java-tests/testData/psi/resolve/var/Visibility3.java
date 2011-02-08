class Test {
  static class ABC{
    public int i = 0;
  }
  static {
    System.out.println("" + getABC().<ref>i);
  }

  static ABC getABC(){
    return new ABC();
  }
}
