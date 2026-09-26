// "Swap 'if' statements" "true-preview"
class A {

  void m() {

    if (cond1) m1();
    el<caret>se if (cond2) m2();
    else if (cond3) m3();

  }

}