class Test {
  <T extends S, S extends Long, K extends Long & Runnable> void method1(T param, S param1, K param2) {
    long l = param;
    long l1 = param1;
    long l2 = param2;
  }
}