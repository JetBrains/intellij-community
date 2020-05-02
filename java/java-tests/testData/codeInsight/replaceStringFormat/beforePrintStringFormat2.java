// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;
import java.util.Locale;

import static java.lang.String.format;

class Main {
  static {
    String s1 = f<caret>ormat("test");
    String s1n = format("test%n");
    String s2 = format(Locale.US, "test");
    String s2n = format(Locale.US, "test%n");
    String s3 = String.format("test");
    String s3l = String.format(Locale.US, "test");
    String s3n = String.format(Locale.US, "test%n");

    System.out.println(String.format(/* one */ Locale.CANADA /* two */, /* three */ "hello, " /* four */));
  }

  Main() {
    String s1 = format("test");
    String s1n = format("test%n");
    String s2 = format(Locale.US, "test");
    String s2n = format(Locale.US, "test%n");
    String s3 = String.format("test");
    String s3l = String.format(Locale.US, "test");
    String s3n = String.format(Locale.US, "test%n");
  }
  void f() {
    String s1 = format("test");
    String s1n = format("test%n");
    String s2 = format(Locale.US, "test");
    String s2n = format(Locale.US, "test%n");
    String s3 = String.format("test");
    String s3l = String.format(Locale.US, "test");
    String s3n = String.format(Locale.US, "test%n");
  }
}
