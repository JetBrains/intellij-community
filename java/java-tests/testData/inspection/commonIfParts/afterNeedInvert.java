// "Merge 'else if' statement inverting the second condition" "true"

class Test {
  String[] foo(Object[] value, String[] defaultValue) {
    if (value == null || !(value instanceof String[])) {
      return defaultValue;
    } else {
      return (String[]) value;
    }
  }
}