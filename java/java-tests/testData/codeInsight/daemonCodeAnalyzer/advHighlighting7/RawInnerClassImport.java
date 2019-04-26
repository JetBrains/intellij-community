package p;

import p2.GenericClass;

class Class1 extends GenericClass<Integer> {
  public void map(InnerClass context) {
    Class2.test(context);
  }
}
