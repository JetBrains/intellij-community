// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;
import java.util.Locale;

import static java.lang.String.format;

class Main {
  static {
    System.out.print(/* one */ "hello" /* two */);
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(/* one */  /* two */ /* three */ ("hello") /* four */);
    System.out.print(/* one */  /* two */ /* three */ ("hello") /* four */);
    System.out.print(/* one */  /* two */ /* three */ ("hello") /* four */);
    System.out.print((format(/* one */ Locale.CANADA /* two */, /* three */ "hello%n" /* four */)));
  }

  Main() {
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(/* one */  /* two */ /* three */ ("hello") /* four */);
    System.out.print(/* one */  /* two */ /* three */ ("hello") /* four */);
  }
  void f() {
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(/* one */ ("hello") /* two */);
    System.out.print(/* one */  /* two */ /* three */ ("hello") /* four */);
    System.out.print(/* one */  /* two */ /* three */ ("hello") /* four */);
  }
  void out(PrintStream printer) {
    printer.print(/* one */ ("hello") /* two */);
    printer.print(/* one */ ("hello") /* two */);
    printer.print(/* one */  /* two */ /* three */ ("hello") /* four */);
    printer.print(/* one */  /* two */ /* three */ ("hello") /* four */);
  }
  void caller() {
    printf(String.format(/* one */ ("hello") /* two */));
    printf(((String.format(/* one */ ("hello") /* two */))));
    printf(String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */));
    printf((String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */)));
  }

  static void printf(String value) {}
}
