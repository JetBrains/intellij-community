// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
import java.io.PrintStream;
import java.util.Locale;

class Main {
  static {
    System.out.printf("%s, %s!", "Hello", "World");
    System.out.printf(
      /* condition start */ true /* condition end */
                            ? /* first leg start */ "%s, %s!" /* first leg end */
                            : /* second leg start */ "%s: %s" /* second leg end */,
      /* first arg start */ "Hello"/* first arg end */,
      /* second arg start */ "World" /* second arg end */);
    System.out.printf(/* one */ Locale.US, /* two */ "%s, %s!" /* three */, /* four */ "Hello" /* five */, /* six */ "World" /* seven */);
    System.out.print("hello" + String.format("%n"));
  }

  Main() {
    System.out.printf("%s, World!", "Hello");
    System.out.printf("%s, %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!" + 5, /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7), /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.print("========");
  }
  void f() {
    System.out.printf("%s, World!", "Hello");
    System.out.printf("%s, %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!" + 5, /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7), /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf("%s, %s", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    System.out.print("========");
  }
  void out(PrintStream printer) {
    printer.printf("%s, World!", "Hello");
    printer.printf("%s, %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf(/* one */ "%s," + /* two */ " %s!", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf(/* one */ "%s," + /* two */ " %s!" + 5, /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf(/* one */ "%s," + /* two */ " %s!" + (5 /* four */ + /* five */ 7), /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf(/* one */ Locale.US, /* two */ "%s, %s!" /* three */, /* four */ "Hello" /* five */, /* six */ "World" /* seven */);
    printer.printf("%s, %s", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.printf("%s, %s%n", /* param1 start */ "Hello" /* param1 end */, /* param2 start */ "World" /* param2 end */);
    printer.print("========");
  }
  void caller() {
    print(String.format("%s, %s!", "Hello", "World"));
  }

  static void print(String value) {}
}
