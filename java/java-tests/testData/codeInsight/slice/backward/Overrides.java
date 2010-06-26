public class OverrideSlice {
    interface I {
        int f(int i);
    }
    class O implements I {
        public int f(int i) {
            return <flown11>i;
        }
    }
    class O0 implements I {
        public int f(int i) {
            return <flown12>0;
        }
    }

    {
        f(<flown1111>1, new O());
    }
    void f(int c, I i) {
        int <caret>x = <flown1>i.f(<flown111>c);
    }
}
