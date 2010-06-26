package p2;

import p1.C1;

public class C2 extends C1 {
    public void amethod() {
        InnerClassInBase inner;
        inner = new <error descr="'p1.C1.InnerClassInBase' has protected access in 'p1.C1'">InnerClassInBase</error>();
    }
}
