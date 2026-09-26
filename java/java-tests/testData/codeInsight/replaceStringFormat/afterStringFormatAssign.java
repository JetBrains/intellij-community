// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;
import java.util.Locale;

import static java.lang.String.format;

class Main {
  static {
    String s1 = "test";
    String s1n = format("test%n");
    String s1n1 = format(("test%n"));
    String s2 = "test";
    String s21 = "test";
    String s2n = format(Locale.US, "test%n");
    String s2n1 = format(Locale.US, ((("test%n"))));
    String s3 = "test";
    String s31 = "test";
    String s3l = "test";
    String s3l1 = "test";
    String s3n1 = String.format(Locale.US, ("test%n"));

    System.out.println(/* one */  /* two */ /* three */ "hello" /* four */);
    System.out.println(/* one */  /* two */ /* three */ ("hello") /* four */);
  }

  Main() {
    String s1 = "test";
    String s1n = format("test%n");
    String s2 = "test";
    String s2n = format(Locale.US, "test%n");
    String s3 = "test";
    String s3l = "test";
    String s3n = String.format(Locale.US, "test%n");
  }
  void f() {
    String s1 = "test";
    String s1n = format("test%n");
    String s2 = "test";
    String s2n = format(Locale.US, "test%n");
    String s3 = "test";
    String s3l = "test";
    String s3n = String.format(Locale.US, "test%n");
  }
}
