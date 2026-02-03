package p2;

import p1.ParentUniqueName;

public class Usage {
    public void test() {
        equals(new ParentUniqueName() {
            public void test() {
            }
        });
    }
}