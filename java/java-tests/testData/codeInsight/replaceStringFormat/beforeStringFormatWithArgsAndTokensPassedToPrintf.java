// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;
import java.util.Locale;

import static java.lang.String.format;

class Main {
  static {
    System.out.<caret>printf(String.format(/* one */ "hello" /* two */));
    System.out.printf(String.format(/* one */ ("hello") /* two */));
    System.out.printf(format(/* one */ ("hello") /* two */);
    System.out.printf(((String.format(/* one */ ("hello") /* two */))));
    System.out.printf(String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */));
    System.out.printf((String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */)));
    System.out.printf((format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */)));
    System.out.printf((format(/* one */ Locale.CANADA /* two */, /* three */ "hello%n" /* four */)));
  }

  Main() {
    System.out.printf(String.format(/* one */ ("hello") /* two */));
    System.out.printf(((String.format(/* one */ ("hello") /* two */))));
    System.out.printf(String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */));
    System.out.printf((String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */)));
  }
  void f() {
    System.out.printf(String.format(/* one */ ("hello") /* two */));
    System.out.printf(((String.format(/* one */ ("hello") /* two */))));
    System.out.printf(String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */));
    System.out.printf((String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */)));
  }
  void out(PrintStream printer) {
    printer.printf(String.format(/* one */ ("hello") /* two */));
    printer.printf(((String.format(/* one */ ("hello") /* two */))));
    printer.printf(String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */));
    printer.printf((String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */)));
  }
  void caller() {
    printf(String.format(/* one */ ("hello") /* two */));
    printf(((String.format(/* one */ ("hello") /* two */))));
    printf(String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */));
    printf((String.format(/* one */ Locale.CANADA /* two */, /* three */ ("hello") /* four */)));
  }

  static void printf(String value) {}
}
