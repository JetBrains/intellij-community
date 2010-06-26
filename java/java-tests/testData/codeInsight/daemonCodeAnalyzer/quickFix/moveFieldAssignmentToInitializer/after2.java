// "Move assignment to field declaration" "true"

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