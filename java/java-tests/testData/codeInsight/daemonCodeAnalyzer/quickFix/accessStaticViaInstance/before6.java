// "Access static 'AClass.R.rr' via class 'R' reference" "true"

class AClass
{
    public static class R {
        static int rr = 0;
    }
    public R getR() {
        return null;
    }
}
class ss {
    void f(AClass d){
        int i = <caret>d.getR().rr;
    }

}
