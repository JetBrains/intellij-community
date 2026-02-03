public class C {
    public <caret>C(int i) {
    }
}

class Usage {
    {
        C c = new C(10);
    }
}