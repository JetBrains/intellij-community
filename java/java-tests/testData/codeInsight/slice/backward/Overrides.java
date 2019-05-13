class OverrideSlice {
    interface I {
        int f(int i);
    }
    class O implements I {
        public int f(int <flown111>i) {
            return <flown11>i;
        }
    }
    class O0 implements I {
        public int f(int i) {
            return <flown12>0;
        }
    }

    {
        f(<flown111111>1, new O());
    }
    void f(int <flown11111>c, I i) {
        int <caret>x = <flown1>i.f(<flown1111>c);
    }
}
