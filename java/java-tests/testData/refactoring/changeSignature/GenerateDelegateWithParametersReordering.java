class C {
    void <caret>method(int i, String s) {
        System.out.println("i = " + i + " s = " + s);
    }
}

class C1 extends C {
    void method(int i, String s) {
        System.out.println("i = " + i + " s = " + s);
    }
}

class Usage {
    {
        new C().method(1, null);
        new C1().method(1, null);
    }
}