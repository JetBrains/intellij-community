// "Swap If Statements" "false"
class A {
  void m() {
    if (someCondition) {
      doSomeAction();
    } e<caret>lse {
      defaultAction();
    }
  }
}