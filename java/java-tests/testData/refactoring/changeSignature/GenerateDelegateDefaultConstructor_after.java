public class C {
    public C() {
        this(27);
    }

    public C(int i) {
    }
}

public class C1 extends C {
    public C1(String s) {
    }
}

class Usage {
    {
        C c = new C();
    }
}