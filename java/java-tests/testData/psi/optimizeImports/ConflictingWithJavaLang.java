package foo;

import foo.A.Person;
import foo.A.OtherClass;
import foo.A.OtherClass1;
import foo.A.OtherClass2;
import foo.A.OtherClass3;
import foo.A.OtherClass4;

class Client {
  public void method(OtherClass otherClass) {
    OtherClass1 o1;
    OtherClass2 o2;
    OtherClass3 o3;
    OtherClass4 o4;
    Person person = null;
  }

  @Override
  public String toString() {
    return super.toString();
  }
}

class A {
  public interface Person {}
  public static class OtherClass {}
  public static class OtherClass1 {}
  public static class OtherClass2 {}
  public static class OtherClass3 {}
  public static class OtherClass4 {}
  public static class Override {}
}