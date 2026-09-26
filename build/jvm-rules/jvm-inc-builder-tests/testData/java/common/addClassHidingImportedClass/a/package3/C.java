package package3;

import package2.B;

public class C {

    public B.D p;

    public String get() {
        p = new B.D();
        return p.s;
    }

}
