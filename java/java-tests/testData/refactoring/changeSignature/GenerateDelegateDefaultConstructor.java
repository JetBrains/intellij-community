public class C {
    public <caret>C() {
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