package p1;

import static p2.Statics.PUB_CONST;

public class Inlined {
    public int myInt = PUB_CONST;

    public Inlined() {
       this(PUB_CONST);
    }

    public Inlined(int i) {
        myInt = i;
    }
}
