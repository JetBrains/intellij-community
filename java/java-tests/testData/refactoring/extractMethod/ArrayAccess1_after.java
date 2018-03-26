class Test {
  void foo(String[] ss, String[] bb) {
     for(int i = 0; i < ss.length; i++) {

         newMethod(ss[i], bb[i]);

     }
  }

    private void newMethod(String s, String s1) {
        System.out.println(s);
        System.out.println(s1);
    }
}