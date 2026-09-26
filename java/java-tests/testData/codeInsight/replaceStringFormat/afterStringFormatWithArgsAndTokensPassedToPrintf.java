// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;
import java.util.Locale;

import static java.lang.String.format;

class Main {
  static {
    System.out.print(/* one */ "hello" /* two */);
    System.out.print(/* one */ ("hello") /* two */;
    System.out.print(((/* one */ ("hello") /* two */)));
  }

  Main() {
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(((/* one */ ("hello") /* two */)));
  }
  void f() {
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(((/* one */ ("hello") /* two */)));
  }
  void out(PrintStream printer) {
    printer.print(/* one */ ("hello") /* two */);
    printer.print(((/* one */ ("hello") /* two */)));
  }
  void caller() {
    printf(/* one */ ("hello") /* two */);
    printf(((/* one */ ("hello") /* two */)));
  }

  static void printf(String value) {}
}
