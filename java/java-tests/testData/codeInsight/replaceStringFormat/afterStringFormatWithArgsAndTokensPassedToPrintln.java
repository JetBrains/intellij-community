// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;

class Main {
  static {
    System.out.printf("%s, %s!%n", "Hello", "World");
    System.out.printf(
      /* condition start */ (false /* condition end */
                    ? /* first leg start */ "%s, %s!" /* first leg end */
                    : /* second leg start */ "%s: %s") + "%n" /* second leg end */,
      /* first arg start */ "Hello"/* first arg end */,
      /* second arg start */ "World" /* second arg end */);
    System.out.println("hello" + String.format("%n"))
  }

  Main() {
    System.out.printf("Hello, World!%n%n");
    System.out.printf("%s, World!%n", "Hello");
    System.out.printf("%s, %s!%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!" + "5%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7) + "%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.println("========");
  }
  void f() {
    System.out.printf("Hello, World!%n%n");
    System.out.printf("%s, World!%n", "Hello");
    System.out.printf("%s, %s!%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!" + "5%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7) + "%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf("%s, %s", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.println("========");
  }
  void out(PrintStream printer) {
    printer.printf("Hello, World!%n%n");
    printer.printf("%s, World!%n", "Hello");
    printer.printf("%s, %s!%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf(/* one */ "%s," + /* two */ " %s!%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf(/* one */ "%s," + /* two */ " %s!" + "5%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7) + "%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf("%s, %s", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.println("========");
  }
  void caller() {
    println(String.format("%s, %s!", "Hello", "World"));
  }

  static void println(String value) {}
}
