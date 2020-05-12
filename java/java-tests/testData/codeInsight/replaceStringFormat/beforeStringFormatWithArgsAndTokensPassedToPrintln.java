// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;

class Main {
  static {
    System.out.println(String.<caret>format("%s, %s!", "Hello", "World"));
    System.out.println(String.format(
      /* condition start */ false /* condition end */
                            ? /* first leg start */ "%s, %s!" /* first leg end */
                            : /* second leg start */ "%s: %s" /* second leg end */,
      /* first arg start */ "Hello"/* first arg end */,
      /* second arg start */ "World" /* second arg end */));
    System.out.println("hello" + String.format("%n"))
  }

  Main() {
    System.out.println(String.format("Hello, World!%n"));
    System.out.println(String.format("%s, World!", "Hello"));
    System.out.println(String.format("%s, %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    System.out.println(String.format(/* one */ "%s," + /* two */ " %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    System.out.println(String.format(/* one */ "%s," + /* two */ " %s!" + 5, /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    System.out.println(String.format(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7), /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    System.out.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.println("========");
  }
  void f() {
    System.out.println(String.format("Hello, World!%n"));
    System.out.println(String.format("%s, World!", "Hello"));
    System.out.println(String.format("%s, %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    System.out.println(String.format(/* one */ "%s," + /* two */ " %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    System.out.println(String.format(/* one */ "%s," + /* two */ " %s!" + 5, /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    System.out.println(String.format(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7), /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    System.out.printf("%s, %s", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.println("========");
  }
  void out(PrintStream printer) {
    printer.println(String.format("Hello, World!%n"));
    printer.println(String.format("%s, World!", "Hello"));
    printer.println(String.format("%s, %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    printer.println(String.format(/* one */ "%s," + /* two */ " %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    printer.println(String.format(/* one */ "%s," + /* two */ " %s!" + 5, /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    printer.println(String.format(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7), /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */));
    printer.printf("%s, %s", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.println("========");
  }
  void caller() {
    println(String.format("%s, %s!", "Hello", "World"));
  }

  static void println(String value) {}
}
