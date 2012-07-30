import java.io.File;

class Foo {
    {
      File file = new File("some.txt");
        System.out.println("file = " + File.<caret>);
    }
}