package main;

public class Main {
  public static void main(String[] args) {
    pkg.sub.Test obj = new pkg.sub.Test();
    obj.<error descr="Cannot resolve method 'm1' in 'Test'">m1</error>();
    obj.m2();

    pkg.sub.Test.<error descr="Cannot resolve method 's1' in 'Test'">s1</error>();
    pkg.sub.Test.s2();
  }
}
