import java.io.*;

class X {

  void processorMissing() {
    System.out.println(<error descr="String templates are not supported at language level '23'">"""
      \{1}
      """</error>);
    <error descr="String templates are not supported at language level '23'">"\{}"</error>;
    System.out.println(<error descr="Cannot resolve symbol 'NOPE'">NOPE</error>.<error descr="String templates are not supported at language level '23'">"\{false}"</error>);
    System.out.println(<error descr="Cannot resolve symbol 'RAW'">RAW</error>.<error descr="String templates are not supported at language level '23'">"\{false}"</error>);
  }

  void correct(int i) {
    System.out.println(<error descr="Cannot resolve symbol 'STR'">STR</error>.<error descr="String templates are not supported at language level '23'">"the value is \{i}"</error>);
    String s = <error descr="Cannot resolve symbol 'STR'">STR</error>."";
    StringTemplate st = <error descr="String templates are not supported at language level '23'">StringTemplate.RAW."""
      """</error>;
  }

  void wrongType(String foo) {
    String s = StringTemplate.RAW.<error descr="String templates are not supported at language level '23'">"""
      this: \{foo}
      """</error>;

    var x = (java.io.Serializable & StringTemplate.Processor<String, RuntimeException>)null;
    java.util.ArrayList v = <error descr="String templates are not supported at language level '23'">x."asdf"</error>;
    String t = <error descr="String templates are not supported at language level '23'">x."reticulation"</error>;
  }

  String unresolvedValues() {
    return <error descr="Cannot resolve symbol 'STR'">STR</error>."\{<error descr="Cannot resolve symbol 'logic'">logic</error>} \{<error descr="Cannot resolve symbol 'proportion'">proportion</error>}";
  }

  interface MyProcessor extends StringTemplate.Processor {}

  String raw(StringTemplate.Processor processor, MyProcessor myProcessor) {
    System.out.println(<error descr="String templates are not supported at language level '23'">myProcessor.""</error>);
    return processor.<error descr="String templates are not supported at language level '23'">"\{}\{}\{}\{}\{}\{}"</error>;
    var z = (java.io.Serializable & StringTemplate.Processor)myProcessor;
    System.out.println(<error descr="String templates are not supported at language level '23'">z.""</error>);
  }

  void nested() {
    System.out.println(<error descr="Cannot resolve symbol 'STR'">STR</error>."\{<error descr="Cannot resolve symbol 'STR'">STR</error>."\{<error descr="Cannot resolve symbol 'STR'">STR</error>."\{<error descr="Cannot resolve symbol 'STR'">STR</error>."\{<error descr="Cannot resolve symbol 'STR'">STR</error>."\{<error descr="Cannot resolve symbol 'STR'">STR</error>."\{<error descr="Cannot resolve symbol 'STR'">STR</error>.""}"}"}"}"}"}");
  }

  String badEscape() {
    System.out.println(<error descr="Cannot resolve symbol 'STR'">STR</error>."b<error descr="Illegal escape character in string literal">\a</error>d \{} esc<error descr="Illegal escape character in string literal">\a</error>pe 1");
    System.out.println(<error descr="Cannot resolve symbol 'STR'">STR</error>. """
      b<error descr="Illegal escape character in string literal">\a</error>d \{} esc<error descr="Illegal escape character in string literal">\a</error>pe 2
      """);
    System.out.println(<error descr="Cannot resolve symbol 'STR'">STR</error>."\{<error descr="Line end not allowed in string literals">}unclosed);</error>
    return <error descr="Cannot resolve symbol 'STR'">STR</error>."\{} <error descr="Illegal Unicode escape sequence">\u</error>X";
  }

  static class Covariant implements StringTemplate.Processor<Object, RuntimeException> {
    @Override
    public Integer process(StringTemplate stringTemplate) {
      return 123;
    }
  }

  public static void testCovariant() {
    Covariant proc = new Covariant();
    // In Java 22, covariant processors are supported
    Integer i = <error descr="String templates are not supported at language level '23'">proc."hello"</error>;
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
      Object x = <error descr="String templates are not supported at language level '23'">proc."hello"</error>;
    }
    catch (Ex ex) {} 
  }

  public static void testExceptionInFragments(StringTemplate.Processor<Integer, Ex> proc,
                                              StringTemplate.Processor<Integer, Ex2> proc2) {
    try {
      proc."hell\{<error descr="String templates are not supported at language level '23'">proc2."xyz"</error>}o";
    }
    catch (Ex ex) {} 
  }

  public static void testCovariantException() {
    CovariantException proc = new CovariantException();
    // As of Java 21, covariant processors are not supported
    Integer i = <error descr="String templates are not supported at language level '23'">proc."hello"</error>;

    try {
      Integer i2 = <error descr="String templates are not supported at language level '23'">proc."hello"</error>;
    }
    catch (Ex ex) {}
  }

  public static void testCapturedWilcard(StringTemplate.Processor<?, ?> str) {
    Object s = <error descr="String templates are not supported at language level '23'">str.""</error>;
  }

  void testCapturedWildcard2() {
    StringTemplate.Processor<StringTemplate.Processor<?, ? extends Exception>, RuntimeException> processor = null;
    Object o = <error descr="String templates are not supported at language level '23'">processor."""
                """</error>."""
                """;
  }
  
  public static void noNewlineAfterTextBlockOpeningQuotes() {
    System.out.println(<error descr="Cannot resolve symbol 'STR'">STR</error>.<error descr="Illegal text block start: missing new line after opening quotes">"""</error>\{}""");
  }
  
  public static void voidExpression() {
    String a = <error descr="Cannot resolve symbol 'STR'">STR</error>.<error descr="String templates are not supported at language level '23'">"\{voidExpression()}"</error>;
    System.out.println(a);
  }

  interface AnyProcessor extends StringTemplate.Processor<Object, Throwable> {}

  interface FooProcessor extends AnyProcessor {
    @Override
    Object process(StringTemplate stringTemplate) throws Ex, IOException;
  }

  interface BarProcessor extends AnyProcessor {
    @Override
    Object process(StringTemplate stringTemplate) throws Ex2, EOFException, FileNotFoundException;
  }

  interface FooBarProcessor extends FooProcessor, BarProcessor {}

  static void test(FooBarProcessor fooBarProcessor) {
    System.out.println(<error descr="String templates are not supported at language level '23'">fooBarProcessor.""</error>);
  }

  static class IntegerProcessor implements StringTemplate.Processor<Object, RuntimeException> {
    @Override
    public Integer process(StringTemplate template) {
      return 1;
    }
  }

  void myTest() {
    Integer x = <error descr="String templates are not supported at language level '23'">new IntegerProcessor()."hello"</error>;
    System.out.println(x);
  }
}