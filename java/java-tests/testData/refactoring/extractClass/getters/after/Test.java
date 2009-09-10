class Test {
    final Extracted extracted = new Extracted();

    public int getMyT() {
        return extracted.getMyT();
    }
  void bar(){
    int i = extracted.getMyT();
  }
}