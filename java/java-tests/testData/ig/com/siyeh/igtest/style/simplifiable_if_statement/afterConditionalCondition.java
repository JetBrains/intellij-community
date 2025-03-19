// "Replace 'if else' with '?:'" "INFORMATION"

class ConditionalCondition {

  String s;
  String t;

  public boolean equals(Object other) {
    if (!(other instanceof ConditionalCondition)) return false;
    final ConditionalCondition condition = (ConditionalCondition)other;


      //end line comment
      return (s != null ? !s.equals(condition.s) : condition.s != null) ? Math.random() > 0.5 : t.equals(condition.t);
  }
}