// "Remove redundant initializer" "true-preview"
class A {
  int n = <caret>0;
  { n = 1; }
}