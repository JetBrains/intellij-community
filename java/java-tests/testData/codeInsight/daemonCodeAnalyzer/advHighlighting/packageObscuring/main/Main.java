package main;

public class Main {
  public static void main(String[] args) {
    pkg.sub.Test obj = new pkg.sub.Test();
    obj.m1();
    obj.<error descr="Cannot resolve method 'm2' in 'Test'">m2</error>();

    pkg.sub.Test.s1();
    pkg.sub.Test.<error descr="Cannot resolve method 's2' in 'Test'">s2</error>();
  }
}
