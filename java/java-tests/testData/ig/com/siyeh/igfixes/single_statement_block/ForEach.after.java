class X {
  void f(int[] a){
      //3
      //4
      //7
      //8
      for(int i: a/*1*/) /*2*/ System.out.println(i/*5*/);//6
    //9
  }
}