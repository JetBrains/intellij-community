// "Remove redundant initializer" "true"
class A {
  int n = <caret>0;
  { n = 1; }
}