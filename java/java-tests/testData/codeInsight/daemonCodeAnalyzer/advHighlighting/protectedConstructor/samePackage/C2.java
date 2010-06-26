package samePackage;

import samePackage.C1;

public class C2 extends C1 {
    public C2() {
        super(9);
    }

    public void amethod() {
        new C1(9); // <<<<---------- This is an error
    }
}
