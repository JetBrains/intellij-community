class c {
  String foo(String s) {
    if (s.length() >= 2 &&
        ((s.charAt(0) == '\'' && <weak_warning descr="Multiple occurrences of 's.charAt(s.length() - 1)'">s.charAt(s.length() - 1)</weak_warning> == '\'') ||
         (s.charAt(0) == '\"' && <weak_warning descr="Multiple occurrences of 's.charAt(s.length() - 1)'">s.charAt(s.length() - 1)</weak_warning> == '\"'))) {
      s = s.substring(1, s.length() - 1);
    }
    return s;
  }
}