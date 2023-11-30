// "Split into separate declarations" "true"
class MutualDependentForDeclarations {

  void f() {
    for(int /*1*/ a = <caret>/*2*/ 0/*3*/ ,b[/*4*/]/*5*/ =/*6*/ {a};;) {}
  }
}