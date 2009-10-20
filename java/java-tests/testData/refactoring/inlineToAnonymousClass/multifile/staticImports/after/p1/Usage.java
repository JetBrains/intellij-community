package p1;

import static p2.Statics.PUB_CONST;

public class Usage {
    public void test() {
        Object i = new Object() {
            public int myInt = PUB_CONST;
        };
    }
}