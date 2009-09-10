package p1;

import p2.Statics;

public class Usage {
    public void test() {
        Object i = new Object() {
            public int myInt = Statics.PUB_CONST;
        };
    }
}