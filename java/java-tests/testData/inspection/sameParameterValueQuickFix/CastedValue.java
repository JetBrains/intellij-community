class SampleClazz {
  private void handleTree(Short <warning descr="Actual value of parameter 'simflag' is always '(short)0'"><caret>simflag</warning>) {
    foo(simflag);
  }

  void foo(short sss){}


  public static void main(String[] args) {
    new SampleClazz().handleTree((short) 0);
  }
}