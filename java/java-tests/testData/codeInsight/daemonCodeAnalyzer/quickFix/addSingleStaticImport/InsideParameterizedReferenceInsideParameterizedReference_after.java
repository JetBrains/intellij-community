import foo.*;
import foo.Class1.Inner1;

class Class2 {
  public static void main(String[] args) {
    new Class1.Inner2<Inner1>();
  }
}
