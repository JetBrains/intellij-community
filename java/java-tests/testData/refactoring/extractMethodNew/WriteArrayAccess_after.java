class Test {
  void foo(String[] ss) {
     for(int i = 0; i < ss.length; i++) {
         newMethod(ss, i);
     }
  }

    private void newMethod(String[] ss, int i) {
        ss[i] = "";
    }
}