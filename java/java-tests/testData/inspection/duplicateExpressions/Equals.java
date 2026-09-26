class Scratch {
  private String foo(String[] s, Object t) {
    if (t.equals(AND) ||
        (<weak_warning descr="Multiple occurrences of 't.equals(LTLT) || t.equals(GTGT) || t.equals(GTGTGT)'">t.equals(LTLT) || t.equals(GTGT) || t.equals(GTGTGT)</weak_warning>) && s.length > 1) {
      return bar(s[0], s[s.length - 1]);
    }
    else if (t.equals(OR) || t.equals(XOR) ||
             (<weak_warning descr="Multiple occurrences of 't.equals(LTLT) || t.equals(GTGT) || t.equals(GTGTGT)'">t.equals(LTLT) || t.equals(GTGT) || t.equals(GTGTGT)</weak_warning>) && s.length != 0) {
      return bar(s[0]);
    }
    return null;
  }

  private String bar(String... s) {return "";}

  static final String AND = "&", OR = "|", XOR = "^", LTLT = "<<", GTGT = ">>", GTGTGT = ">>>";
}