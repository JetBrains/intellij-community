import java.io.*;
class Foo{
  void foo() throws IO<caret>Exception {
    throw new FileNotFoundException();
  }
}