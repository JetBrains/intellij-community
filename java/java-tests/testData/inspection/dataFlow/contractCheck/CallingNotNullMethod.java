import org.jetbrains.annotations.*;

class Test {
  
  @Contract("!null -> null")
  static String test(String s) {
    if(s != null) {
      return <warning descr="Contract clause '!null -> null' is violated">getValue(s)</warning>;
    }
    return getDefaultValue();
  }

  @NotNull
  static String getValue(String s) {
    return s.trim();
  }

  static String getDefaultValue() {
    return "foo";
  }
}