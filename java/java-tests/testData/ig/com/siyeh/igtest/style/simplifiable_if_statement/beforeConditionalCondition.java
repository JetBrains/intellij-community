// "Replace 'if else' with '?:'" "INFORMATION"

class ConditionalCondition {

  String s;
  String t;

  public boolean equals(Object other) {
    if (!(other instanceof ConditionalCondition)) return false;
    final ConditionalCondition condition = (ConditionalCondition)other;


    if<caret> (s != null ? !s.equals(condition.s) : condition.s != null) return Math.random() > 0.5;
    return t.equals(condition.t);//end line comment
  }
}