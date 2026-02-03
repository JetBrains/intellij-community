import java.io.*;

class Foo {
  public void foo() {
    try {
      throw new EOFException("aaa");
    } catch (Exception e) {
      if (<warning descr="Condition 'e == null' is always 'false'">e == null</warning>) {
        System.out.println("Can't be here.");
      }
      if (<warning descr="Condition 'e instanceof FileNotFoundException' is always 'false'">e instanceof FileNotFoundException</warning>) {
        System.out.println("Can't be here.");
      }
    }
  }
}