class X {

  void processorMissing() {
    System.out.println(<error descr="Processor missing from string template expression">"""
      \{1}
      """</error>);
    <error descr="Processor missing from string template expression">"\{}"</error>;
    System.out.println(<error descr="Cannot resolve symbol 'NOPE'">NOPE</error>."\{false}");
    System.out.println(<error descr="Cannot resolve symbol 'RAW'">RAW</error>."\{false}");
  }

  void correct(int i) {
    System.out.println(STR."the value is \{i}");
    String s = STR."";
    StringTemplate st = StringTemplate.RAW."""
      """;
  }

  void wrongType(String foo) {
    <error descr="Incompatible types. Found: 'java.lang.StringTemplate', required: 'java.lang.String'">String s = StringTemplate.RAW."""
      this: \{foo}
      """;</error>

    var x = (java.io.Serializable & StringTemplate.Processor<String, RuntimeException>)null;
    <error descr="Incompatible types. Found: 'java.lang.String', required: 'java.util.ArrayList'">java.util.ArrayList v = x."asdf";</error>
    String t = x."reticulation";
  }

  String unresolvedValues() {
    return STR."\{<error descr="Cannot resolve symbol 'logic'">logic</error>} \{<error descr="Cannot resolve symbol 'proportion'">proportion</error>}";
  }

  interface MyProcessor extends StringTemplate.Processor {}

  String raw(StringTemplate.Processor processor, MyProcessor myProcessor) {
    System.out.println(<error descr="Raw processor type is not allowed: MyProcessor">myProcessor</error>."");
    return <error descr="Raw processor type is not allowed: Processor">processor</error>."\{}\{}\{}\{}\{}\{}";
    var z = (java.io.Serializable & StringTemplate.Processor)myProcessor;
    System.out.println(<error descr="Raw processor type is not allowed: Serializable & Processor">z</error>."");
  }

  void nested() {
    System.out.println(STR."\{STR."\{STR."\{STR."\{STR."\{STR."\{STR.""}"}"}"}"}"}");
  }

  String badEscape() {
    System.out.println(STR."b<error descr="Illegal escape character in string literal">\a</error>d \{} esc<error descr="Illegal escape character in string literal">\a</error>pe 1");
    System.out.println(STR. """
      b<error descr="Illegal escape character in string literal">\a</error>d \{} esc<error descr="Illegal escape character in string literal">\a</error>pe 2
      """);
    System.out.println(STR."\{<error descr="Line end not allowed in string literals">}unclosed);</error>
    return STR."\{} <error descr="Illegal Unicode escape sequence">\u</error>X";
  }

  static class Covariant implements StringTemplate.Processor<Object, RuntimeException> {
    @Override
    public Integer process(StringTemplate stringTemplate) {
      return 123;
    }
  }

  public static void testCovariant() {
    Covariant proc = new Covariant();
    // As of Java 21, covariant processors are not supported
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.Integer'">Integer i = proc."hello";</error>
  }

  static class CovariantException implements StringTemplate.Processor<Integer, Exception> {
    @Override
    public Integer process(StringTemplate stringTemplate) throws Ex {
      return 123;
    }
  }
  
  class Ex extends Exception {}
  class Ex2 extends Exception {}
  
  public static void testHandle(StringTemplate.Processor<Integer, Ex> proc) {
    try {
      Object x = proc."hello";
    }
    catch (Ex ex) {} 
  }

  public static void testExceptionInFragments(StringTemplate.Processor<Integer, Ex> proc,
                                              StringTemplate.Processor<Integer, Ex2> proc2) {
    try {
      proc."hell\{<error descr="Unhandled exception: X.Ex2">proc2."xyz"</error>}o";
    }
    catch (Ex ex) {} 
  }

  public static void testCovariantException() {
    CovariantException proc = new CovariantException();
    // As of Java 21, covariant processors are not supported
    Integer i = <error descr="Unhandled exception: java.lang.Exception">proc."hello";</error>

    try {
      Integer i2 = <error descr="Unhandled exception: java.lang.Exception">proc."hello";</error>
    }
    catch (Ex ex) {}
  }

  public static void testCapturedWilcard(StringTemplate.Processor<?, ?> str) {
    Object s = <error descr="Unhandled exception: java.lang.Throwable">str."";</error>
  }

  void testCapturedWildcard2() {
    StringTemplate.Processor<StringTemplate.Processor<?, ? extends Exception>, RuntimeException> processor = null;
    Object o = <error descr="Unhandled exception: java.lang.Exception">processor."""
                """."""
                """;</error>
  }
  
  public static void noNewlineAfterTextBlockOpeningQuotes() {
    System.out.println(STR.<error descr="Illegal text block start: missing new line after opening quotes">"""</error>\{}""");
  }
  
  public static void voidExpression() {
    String a = STR."\{<error descr="Expression with type 'void' not allowed as string template embedded expression">voidExpression()</error>}";
    System.out.println(a);
  }
}