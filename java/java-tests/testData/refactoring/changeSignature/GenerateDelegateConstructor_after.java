public class C {
    public C(int i) {
        this();
    }

    public C() {
    }
}

class Usage {
    {
        C c = new C(10);
    }
}