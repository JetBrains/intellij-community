// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
package java.lang;

import java.io.PrintStream;

class String {
  String(Object original) {}

  public native String formatted(Object... args);
}

class Main {
  static {
      /* one */
      System.out.printf(/* three */(new String("""
              %s, %s!
              """/* two */)) + "%n", "Hello"/* four */, /* six */ "World" /* seven */);
      /* one */
      System.out.printf(/* six */( /* two */ ( /* three */ new String("""
                                           %s, %s!
                                           """/* four */) /* five */)), "Hello"/* seven */, /* eight */ "World" /* nine */);

      /* one */
      System.out.printf(/* six */new String("""
      %s, %s!
      """/* two */), "Hello"/* seven */, /* eight */ "World" /* nine */);

    System.out.printf(/* five */( /* one */ new String("""
            %s,
            """ /* two */) + /* three */
            new String("""
                    %s!
                    """) + "%n"/* four */), "Hello"/* six */, /* seven */ "World" /* eight */);

    System.out.printf(/* five */( /* one */ new String("""
             %s,
             """ /* two */) +
new String(/* three */
  """
    %s!
    """)/* four */), "Hello"/* six */, /* seven */ "World" /* eight */);
  }

  void out(PrintStream printer) {
      /* one */
      printer.printf(/* three */(new String("""
              %s, %s!
              """/* two */)) + "%n", "Hello"/* four */, /* six */ "World" /* seven */);
      /* one */
      printer.printf(/* six */( /* two */ ( /* three */ new String("""
                                           %s, %s!
                                           """/* four */) /* five */)), "Hello"/* seven */, /* eight */ "World" /* nine */);

      /* one */
      printer.printf(/* six */new String("""
      %s, %s!
      """/* two */), "Hello"/* seven */, /* eight */ "World" /* nine */);

    printer.printf(/* five */( /* one */ new String("""
            %s,
            """ /* two */) + /* three */
            new String("""
                    %s!
                    """) + "%n"/* four */), "Hello"/* six */, /* seven */ "World" /* eight */);

    printer.printf(/* five */( /* one */ new String("""
             %s,
             """ /* two */) +
new String(/* three */
  """
    %s!
    """)/* four */), "Hello"/* six */, /* seven */ "World" /* eight */);
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
