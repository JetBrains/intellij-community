// "Swap 'if' statements" "false"
class A {
  void m() {
    if (someCondition) {
      doSomeAction();
    } e<caret>lse {
      defaultAction();
    }
  }
}