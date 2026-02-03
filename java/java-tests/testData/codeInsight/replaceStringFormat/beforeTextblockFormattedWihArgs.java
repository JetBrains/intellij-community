// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
package java.lang;

import java.io.PrintStream;

class String {
  String(Object original) {}

  public native String formatted(Object... args);
}

class Main {
  static {
    System.out.println(/* one */ new String("""
                                              %s, %s!
                                              """/* two */).<caret>formatted(/* three */"Hello"/* four */, /* six */ "World" /* seven */));
    System.out.print(/* one */ ( /* two */ ( /* three */ new String("""
                                                                      %s, %s!
                                                                      """/* four */) /* five */))
                                 .formatted(/* six */"Hello"/* seven */, /* eight */ "World" /* nine */));

    System.out.print(/* one */ new String("""
                                            %s, %s!
                                            """/* two */).formatted(/* six */"Hello"/* seven */, /* eight */ "World" /* nine */));

    System.out.println(( /* one */ new String("""
                                                %s,
                                                """ /* two */) + /* three */
                                   new String("""
                                                %s!
                                                """)/* four */).formatted(/* five */"Hello"/* six */, /* seven */ "World" /* eight */));

    System.out.print(( /* one */ new String("""
                                              %s,
                                              """ /* two */) +
                                 new String(/* three */
                                   """
                                     %s!
                                     """)/* four */).formatted(/* five */"Hello"/* six */, /* seven */ "World" /* eight */));
  }

  void out(PrintStream printer) {
    printer.println(/* one */ new String("""
                                           %s, %s!
                                           """/* two */).formatted(/* three */"Hello"/* four */, /* six */ "World" /* seven */));
    printer.print(/* one */ ( /* two */ ( /* three */ new String("""
                                                                   %s, %s!
                                                                   """/* four */) /* five */))
                              .formatted(/* six */"Hello"/* seven */, /* eight */ "World" /* nine */));

    printer.print(/* one */ new String("""
                                         %s, %s!
                                         """/* two */).formatted(/* six */"Hello"/* seven */, /* eight */ "World" /* nine */));

    printer.println(( /* one */ new String("""
                                             %s,
                                             """ /* two */) + /* three */
                                new String("""
                                             %s!
                                             """)/* four */).formatted(/* five */"Hello"/* six */, /* seven */ "World" /* eight */));

    printer.print(( /* one */ new String("""
                                           %s,
                                           """ /* two */) +
                              new String(/* three */
                                """
                                  %s!
                                  """)/* four */).formatted(/* five */"Hello"/* six */, /* seven */ "World" /* eight */));
  }

  void caller() {
    println(( /* one */ new String("""
                                     %s,
                                     """ /* two */ + /* three */
                                   """
                                     %s!
                                     """/* four */)).formatted(/* five */"Hello"/* six */, /* seven */ "World" /* eight */));
  }

  static void println(String value) {}
}
