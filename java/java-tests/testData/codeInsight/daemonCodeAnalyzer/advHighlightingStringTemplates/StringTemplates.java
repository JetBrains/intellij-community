class X {

  void processorMissing() {
    System.out.println(<error descr="Processor missing from string template expression">"""
      \{1}
      """</error>);
    <error descr="Processor missing from string template expression">"\{}"</error>;
    System.out.println(<error descr="Cannot resolve symbol 'NOPE'">NOPE</error>."\{false}");
  }

  void correct(int i) {
    System.out.println(STR."the value is \{i}");
  }

  String unresolvedValues() {
    return STR."\{<error descr="Cannot resolve symbol 'logic'">logic</error>} \{<error descr="Cannot resolve symbol 'proportion'">proportion</error>}";
  }
}