// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;

class Main {
  static {
    System.out.print(String.<caret>format(/* begin */ "Hello, World!"/* end */));
    System.out.print(String.format((/* begin */ "Hello, World!"/* end */)));
    System.out.print(String.format(/* begin */ "Hello, World!%n"/* end */));
    System.out.print(String.format((/* begin */ "Hello, World!%n"/* end */)));
  }

  Main() {
    System.out.print(String.format(/* begin */ "Hello, World!"/* end */));
    System.out.print(String.format(/* begin */ "Hello, World!%n"/* end */));
  }
  void f() {
    System.out.print(String.format(/* begin */ "Hello, World!"/* end */));
    System.out.print(String.format(/* begin */ "Hello, World!%n"/* end */));
  }
  void out(PrintStream printer) {
    printer.print(String.format(/* begin */ "Hello, World!"/* end */));
    printer.print(String.format(/* begin */ "Hello, World!%n"/* end */));
  }
  void caller() {
    print(String.format(/* begin */ "Hello, World!"/* end */));
    print(String.format(/* begin */ "Hello, World!%n"/* end */));
  }

  static void print(String value) {}
}
