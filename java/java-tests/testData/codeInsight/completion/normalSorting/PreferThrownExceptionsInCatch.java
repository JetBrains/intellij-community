import java.io.*;

class Foo {
  {
    try {
      foo();
    } catch (<caret>)
  }

  private void foo() throws FileNotFoundException {
  }

  private void bar() throws ArrayIndexOutOfBoundsException {
  }
}