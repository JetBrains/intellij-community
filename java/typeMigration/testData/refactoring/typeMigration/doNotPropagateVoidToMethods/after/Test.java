class Test {

  void x(){
      notSoPureFunction(10);
  }

  int notSoPureFunction(int i) {
    System.out.println("i = " + i);
    return i+1;
  }
}