// access static via instance
class AClass
{
    public int get() {
        int i = <warning descr="Static member 'AClass.fff' accessed via instance reference">this.fff</warning>;
        return i;
    }
    public static AClass getA() {
        return null;
    }

    Object gg()
    {
      return <warning descr="Static member 'AClass.getA()' accessed via instance reference">this.getA</warning>();
    }
    static int fff;

    protected static class R {
        static int rr = 0;
    }
    public R getR() {
        return null;
    }
}

class anotherclass {
    int f(AClass d){
        int i = <warning descr="Static member 'AClass.R.rr' accessed via instance reference">d.getR().rr</warning>;
        return i;
    }
}
