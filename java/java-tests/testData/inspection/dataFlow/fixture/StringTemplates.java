import typeUse.NotNull;
import typeUse.Nullable;

class Main {
  void testNull() {
    StringTemplate.Processor<String, RuntimeException> proc = null;
    String s = <warning descr="Template processor invocation will produce 'NullPointerException'">proc</warning>."Hello";
  }

  static class NullableProcessor implements StringTemplate.Processor<String, RuntimeException> {
    @Override
    @Nullable
    public native String process(StringTemplate stringTemplate);
  }

  void testNullable(NullableProcessor proc) {
    var res = proc."hello".<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>();
  }

  void testTypeAnno(StringTemplate.Processor<@NotNull String, RuntimeException> proc1,
                    StringTemplate.Processor<@Nullable String, RuntimeException> proc2) {
    if (<warning descr="Condition 'proc1.\"hello\" == null' is always 'false'">proc1."hello" == null</warning>) {}
    System.out.println(proc2."hello".<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
  }

  void testSideEffectsInFragments(StringTemplate.Processor<String, RuntimeException> processor) {
    int i = 0;
    System.out.println(processor. "a\{ i++ }+b\{ <warning descr="Result of 'i - 1' is always '0'">i - 1</warning> }" );
  }

  void testSTR(int x) {
    String s1 = STR."hello";
    if (<warning descr="Condition 's1.equals(\"hello\")' is always 'true'">s1.equals("hello")</warning>) {}
    String s2 = STR."""
    		string template""";
    if (<warning descr="Condition 's2.equals(\"string template\")' is always 'true'">s2.equals("string template")</warning>) {}
    String s3 = STR."a\{123}b";
    if (<warning descr="Condition 's3.equals(\"a123b\")' is always 'true'">s3.equals("a123b")</warning>) {}
    String s4 = STR."x = \{x}";
    if (<warning descr="Condition 's4.length() >= 5 && s4.length() <= 15' is always 'true'"><warning descr="Condition 's4.length() >= 5' is always 'true'">s4.length() >= 5</warning> && <warning descr="Condition 's4.length() <= 15' is always 'true' when reached">s4.length() <= 15</warning></warning>) {}
    String s5 = STR."hello\{}";
    if (<warning descr="Condition 's5.equals(\"hellonull\")' is always 'true'">s5.equals("hellonull")</warning>) {}
  }
  
  void testIncomplete() {
    int x = 1;
    return <error descr="Processor missing from string template expression">"\{x}"</error>;
  }
}