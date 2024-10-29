class Test {
  Test() {}
}
class Cls2 {
  Test test = new Test() {
    void x() {}
  };
  
  
  void use() {
    Test test = new Builder().createTest();
  }
}