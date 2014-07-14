// "Swap If Statements" "true"
class A {

  void m() {

    if (someCondition) {
      doSomeAction();
    } e<caret>lse if (otherCondition) {
      doAnotherAction();
    } else {
      defaultAction();
    }

  }

}