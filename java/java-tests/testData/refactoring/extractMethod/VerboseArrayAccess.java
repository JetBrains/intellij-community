class Test {
  void foo(String[] ss) {
     for(int i = 0; i < ss.length; i++) {
       <selection>
       System.out.println(ss[i]);
       System.out.println(ss[i+1]);
       </selection>
     }
  }
}