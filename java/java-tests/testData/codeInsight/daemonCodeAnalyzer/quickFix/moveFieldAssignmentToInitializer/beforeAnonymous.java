// "Move assignment to field declaration" "false"
class UU extends U {
    Runnable f;
    int f2;

    public UU() {
       final int outer = 0;
       f <caret>= new Runnable() {
           int t;
           public void run() {
              t=0;
              t = outer;
           }
       };
    }
}
