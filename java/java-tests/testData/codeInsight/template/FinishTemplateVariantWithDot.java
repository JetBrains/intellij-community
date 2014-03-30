import java.io.File;

class Foo {
    {
      File file = new File("some.txt");
      <caret>
    }
}