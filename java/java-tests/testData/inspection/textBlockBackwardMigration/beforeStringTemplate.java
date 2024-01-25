// "Fix all 'Text block can be replaced with regular string literal' problems in file" "false"

class StringTemplate {
  void test(StringTemplate.Processor<Integer, RuntimeException> proc) {
    Integer test = proc."""
				Hello<caret>
				World
				""";
  }
}