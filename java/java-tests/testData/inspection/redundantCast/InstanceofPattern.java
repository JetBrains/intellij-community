class InstanceofPattern {
  int length(Object obj) {
    // TODO: Warn about the redundant cast.
    return obj instanceof String str ? ((String)str).length() : 0;
  }

  boolean isEmpty(Object obj) {
    return obj instanceof String str && ((<warning descr="Casting 'str' to 'String' is redundant">String</warning>)str).isEmpty();
  }

  int length(String str) {
    return str instanceof String ? ((<warning descr="Casting 'str' to 'String' is redundant">String</warning>)str).length() : 0;
  }

  boolean isEmpty(String str) {
    return str instanceof String && ((<warning descr="Casting 'str' to 'String' is redundant">String</warning>)str).isEmpty();
  }
}