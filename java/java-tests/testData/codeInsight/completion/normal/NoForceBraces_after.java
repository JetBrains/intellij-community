import java.io.File;

public class Foo {
  void foo(boolean flag) {
      if ('\\' == File.separatorChar<caret>)
      System.out.println();
  }
}
