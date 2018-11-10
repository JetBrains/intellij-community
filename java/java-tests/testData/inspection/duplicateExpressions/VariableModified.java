class C {
  String foo(String s) {
    String t = s;
    int len = s.length();
    if (len >= 2 && (s.charAt(0) == '(' && s.charAt(len - 1) == ')')) {
      t = s.substring(1, len);
    }
    len--;
    if (len >= 2 && (s.charAt(0) == '{' && s.charAt(len - 1) == '}')) {
      t = s.substring(1, len);
    }
    if (len >= 2 && (s.charAt(0) == '[' && s.charAt(len - 1) == ']')) {
      t = s.substring(1, len);
    }
    return t;
  }
}