class InstanceofPattern {
  int length(Object obj) {
    return obj instanceof String str ? ((<warning descr="Casting 'str' to 'String' is redundant">String</warning>)str).length() : 0;
  }

  int length2(Object obj) {
    if (obj instanceof String str) return ((<warning descr="Casting 'str' to 'String' is redundant">String</warning>) str).length();
    return 0;
  }

  String length3(Object obj) {
    return obj instanceof String str ? 1 == 1 ? ((<warning descr="Casting 'str' to 'String' is redundant">String</warning>) str).trim() : "0" : "0";
  }
  int loop(Object object) {
    for (; object instanceof String str; ) {
      return ((<warning descr="Casting 'str' to 'String' is redundant">String</warning>) str).length();
    }
    return 0;
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