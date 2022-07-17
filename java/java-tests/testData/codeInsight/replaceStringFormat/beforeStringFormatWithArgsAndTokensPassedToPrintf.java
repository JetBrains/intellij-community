// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;
import java.util.Locale;

import static java.lang.String.format;

class Main {
  static {
    System.out.<caret>printf(/* one */ "hello" /* two */);
    System.out.printf(/* one */ ("hello") /* two */;
    System.out.printf(((/* one */ ("hello") /* two */)));
  }

  Main() {
    System.out.printf(/* one */ ("hello") /* two */);
    System.out.printf(((/* one */ ("hello") /* two */)));
  }
  void f() {
    System.out.printf(/* one */ ("hello") /* two */);
    System.out.printf(((/* one */ ("hello") /* two */)));
  }
  void out(PrintStream printer) {
    printer.printf(/* one */ ("hello") /* two */);
    printer.printf(((/* one */ ("hello") /* two */)));
  }
  void caller() {
    printf(/* one */ ("hello") /* two */);
    printf(((/* one */ ("hello") /* two */)));
  }

  static void printf(String value) {}
}
