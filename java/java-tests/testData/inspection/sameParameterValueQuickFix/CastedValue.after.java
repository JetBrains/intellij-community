class SampleClazz {
  private void handleTree() {
    foo((short) 0);
  }

  void foo(short sss){}


  public static void main(String[] args) {
    new SampleClazz().handleTree();
  }
}