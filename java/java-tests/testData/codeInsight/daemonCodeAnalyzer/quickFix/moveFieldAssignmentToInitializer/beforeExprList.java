// "Move assignment to field declaration" "false"
class Wtf {
  int myWtf;

  void whatever() {
    <caret>myWtf = Math,max(myWtf, getWtf());
  }

  int getWtf() { return 42; }
}
