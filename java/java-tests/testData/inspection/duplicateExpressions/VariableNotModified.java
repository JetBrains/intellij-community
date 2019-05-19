class C {
  String foo(String s) {
    String t = s;
    int len = s.length();
    if (len >= 3 && (s.charAt(0) == '(' && <weak_warning descr="Multiple occurrences of 's.charAt(len - 2)'">s.charAt(len - 2)</weak_warning> == ')')) {
      t = s.substring(1, len);
    }
    if (len >= 3 && (s.charAt(0) == '{' && <weak_warning descr="Multiple occurrences of 's.charAt(len - 2)'">s.charAt(len - 2)</weak_warning> == '}')) {
      t = s.substring(1, len);
    }
    if (len >= 3 && (s.charAt(0) == '[' && <weak_warning descr="Multiple occurrences of 's.charAt(len - 2)'">s.charAt(len - 2)</weak_warning> == ']')) {
      t = s.substring(1, len);
    }
    return t;
  }
}