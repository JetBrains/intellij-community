// "Merge 'else if' statement inverting the second condition" "true"

class Test {
  String[] foo(Object[] value, String[] defaultValue) {
    i<caret>f (value == null) {
      return defaultValue;
    } else if (value instanceof String[]) {
      return (String[]) value;
    } else {
      return defaultValue;
    }
  }
}