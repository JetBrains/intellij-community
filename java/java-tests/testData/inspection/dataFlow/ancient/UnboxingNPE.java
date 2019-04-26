class Auto {
    public Auto(int k) {
    }

    int f(int k, Auto other) {
        {
            Integer i = null;
            if (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>>0) {
            }
        }

        {
            Integer i = null;
            int i1 = (int) <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>;
        }
        {
            Integer i = null;
            int i1 = <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>+i;
        }
        {
            Integer i = null;
            int i1 = <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>++;
        }
        {
            Integer i = null;
            Integer i1 = <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>++;
        }
        {
            Integer i = null;
            int[] ia = new int[0];
            if (Math.random() > 0.5) {
              int i2 = ia[<warning descr="Array index is out of bounds"><warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning></warning>];
            }
        }
        {
            Integer i = null;
            int[] i2 = {<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>};
        }
        {
            Boolean i = null;
            boolean i2 = this==other;
            i2 &= <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>;
        }
        {
            Boolean i = null;
            boolean i2 = this==other;
            i2 |= <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>;
        }
        {
            Boolean i = null;
            boolean i2 = this==other;
            i2 = !<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>;
        }

        {
            Integer i = null;
            if (this==other) {
                return <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>;
            }
        }
        {
            Integer i = null;
            switch(<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
                case 0:
            }
        }
        {
            Boolean i = null;
            boolean i2 = <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning> && <warning descr="Condition 'i' is always 'true' when reached">i</warning>;
        }
        {
            Boolean i = null;
            boolean i2 = <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning> | i;
        }
        {
            Boolean i = null;
            boolean i2 = true ^ <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>;
        }
        {
            Boolean i = null;
            boolean i2 = <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning> ? true : false;
        }
        {
            Integer i = null;
            f(<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>);
        }
        {
            Integer i = null;
            new Auto(<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>);
        }
        {
            Integer i = null;
            <warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>++;
        }
        {
            Integer i = null;
            --<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>;
        }
        {
            Boolean i = null;
            Boolean i2 = !<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>;
        }

        return 0;
    }

    void f(int i) {}
}
