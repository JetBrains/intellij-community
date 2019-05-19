
enum Test {
  A((<warning descr="Casting '\"\"' to 'String' is redundant">String</warning>) "");

  Test(String s) {
  }
}