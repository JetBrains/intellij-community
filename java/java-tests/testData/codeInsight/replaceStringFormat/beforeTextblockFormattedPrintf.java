// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"

import java.io.PrintStream;

class Main {
  static {
    System.out.<caret>printf(/* one */"""
                                        Hello
                                        """ /* two */);
    System.out.printf(/* three */"""
                                   Hello%n
                                   """ /* four */);
  }

  Main() {
    System.out.printf(/* one */"""
                                 Hello
                                 """ /* two */);
    System.out.printf(/* three */"""
                                   Hello%n
                                   """ /* four */);
  }

  void f() {
    System.out.printf(/* one */"""
                                 Hello
                                 """ /* two */);
    System.out.printf(/* three */"""
                                   Hello%n
                                   """ /* four */);
  }

  void out(PrintStream printer) {
    printer.printf(/* one */"""
                              Hello
                              """ /* two */);
    printer.printf(/* three */"""
                                Hello%n
                                """ /* four */);
  }

  void caller() {
    printf(/* one */"""
                      Hello
                      """ /* two */);
    printf(/* three */"""
                        Hello%n
                        """ /* four */);
  }

  static void printf(String value) {}
}
