package foo;

import foo.A.*;
import foo.A.Person;
import foo.B.BasicPerson;

class Client {
  public void method(OtherClass otherClass) {
    Person person = null;
    BasicPerson person2 = null;
  }
}

class A {
  public interface Person {}
  public static class BasicPerson implements Person {}
  public static class OtherClass {}
}

class B {
  public interface Person {}
  public static class BasicPerson implements Person {}
  public static class OtherClass {}
}