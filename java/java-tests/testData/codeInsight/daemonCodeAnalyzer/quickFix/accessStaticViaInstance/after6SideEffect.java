// "Access static 'R.rr' via class 'R' reference|->Extract possible side effects" "true-preview"

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
        d.getR();
        int i = AClass.R.rr;
    }

}
