/*
Value is always true (!isString; line#13)
  Value 'isString' is always 'false' (isString; line#13)
    'isString == false' was established from condition (isString; line#10)
 */

public class ExplainMe {
  public int foo(Object a) {
    boolean isString = a instanceof String;
    if (isString) {
      return 0;
    }
    if (<selection>!isString</selection>) {
      return 1;
    }
    return 0;

  }
}