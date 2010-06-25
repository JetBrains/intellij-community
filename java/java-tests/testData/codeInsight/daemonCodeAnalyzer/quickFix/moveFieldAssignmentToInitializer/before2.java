// "Move assignment to field declaration" "true"

class X {
  int f;
  X() {
    f = <caret>0;
  }
  X(int i) {
   if (1==1) f=0; else {
       f =   0;//sds
   }
  }
}