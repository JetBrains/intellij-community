class Test {
  void foo(String[] ss, String[] bb) {
     for(int i = 0; i < ss.length; i++) {
       <selection>
       System.out.println(ss[i]);
       System.out.println(bb[i]);
       </selection>
     }
  }
}