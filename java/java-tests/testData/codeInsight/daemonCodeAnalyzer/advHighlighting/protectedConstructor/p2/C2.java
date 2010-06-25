package p2;

import p1.C1;

public class C2 extends C1 {
    public C2() {
        super(9);
    }

    public void amethod() {
        new <error descr="'C1(int)' has protected access in 'p1.C1'">C1</error>(9); 
    }

    Object gg() {
        // anonymous is ok?!
        return new C1(1){};
    }
}
