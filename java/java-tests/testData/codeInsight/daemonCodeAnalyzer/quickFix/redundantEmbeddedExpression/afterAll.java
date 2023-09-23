// "Fix all 'Redundant embedded expression in string template' problems in file" "true"
package java.lang;
import java.util.*;
public interface StringTemplate {
  List<String> fragments();
  List<Object> values();
  native static StringTemplate of(String string);
  Processor<String, RuntimeException> STR;
  Processor<StringTemplate, RuntimeException> RAW;
  interface Processor<R, E extends Throwable> {
    R process(StringTemplate stringTemplate) throws E;
  }
}

class Test {
  public static void main(String[] args) {
    System.out.println(STR."hello|null|world");
    System.out.println(STR."hello|null|world");
    System.out.println(STR."hello||world");
    System.out.println(STR."hello|\r\n|world");
      /*before*/
      /*after*/
      System.out.println(STR."hello|str|world \{1 + 1}");
    System.out.println(STR."""
    			Hello!!! \{"""
                 World"""}""");
    System.out.println(STR."hello|1|world \{1 + 1}");
    System.out.println(STR."hello|1000|world \{1 + 1}");
    System.out.println(STR."hello|\{1_000}|world \{1 + 1}");
    System.out.println(STR."hello|1.0|world \{1 + 1}");
    System.out.println(STR."hello|\{1.0d}|world \{1 + 1}");
  }
}
