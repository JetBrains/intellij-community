// "Access static 'AClass.R.rr' via class 'AClass.R' reference" "false"

class AClass
{
    private static class R {
        static int rr = 0;
    }
    public R getR() {
        return null;
    }
}
// AClass.R is not accessible there
class ss {
    void f(AClass d){
        int i = <caret>d.getR().rr;
    }

}
