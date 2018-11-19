class C {
  String foo(String a, String b, StringBuilder s) {
    if (b.equals(<weak_warning descr="Multiple occurrences of 'a.substring(2) + b.substring(a.length())'">a.substring(2) + b.substring(a.length())</weak_warning>)) {
      return <weak_warning descr="Multiple occurrences of 'a.substring(2) + b.substring(a.length())'">a.substring(2) + b.substring(a.length())</weak_warning>;
    }
    if (s.append(a).append(b.substring(a.length())) != null) {
      return s.append(a).append(b.substring(a.length())).toString();
    }
    return a;
  }
}