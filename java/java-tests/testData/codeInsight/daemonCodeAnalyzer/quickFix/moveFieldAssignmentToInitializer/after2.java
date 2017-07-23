// "Move assignment to field declaration" "INFORMATION"

class X {
  int f = 0;
  X() {
  <caret>}
  X(int i) {
   if (1==1) ;
   else {
   }
  }
}