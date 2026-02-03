import java.io.File;

public class Foo {
  void foo(boolean flag) {
      if ('\\' == File.separaCh<caret>)
      System.out.println();
  }
}
