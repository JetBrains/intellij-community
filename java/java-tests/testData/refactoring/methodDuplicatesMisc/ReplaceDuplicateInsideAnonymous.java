import javax.swing.*;
import java.io.InputStream;
import java.io.PrintStream;

abstract class A {
  protected PrintStream myPrinter;

  protected void fo<caret>oBar() {
    myPrinter.println("hello");
  }
}

class B extends A {
  public void foo() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myPrinter.println("hello");
      }
    });
  }
}
