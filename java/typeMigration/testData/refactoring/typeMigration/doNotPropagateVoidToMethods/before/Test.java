class Test {

  int x(){
    return notSoPureFunction(10) + 1;
  }

  int notSoPureFunction(int i) {
    System.out.println("i = " + i);
    return i+1;
  }
}