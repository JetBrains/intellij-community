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
    System.out.println(<error descr="Raw processor type is not allowed: X.MyProcessor">myProcessor</error>."");
    return <error descr="Raw processor type is not allowed: java.lang.StringTemplate.Processor">processor</error>."\{}\{}\{}\{}\{}\{}";
  }
}