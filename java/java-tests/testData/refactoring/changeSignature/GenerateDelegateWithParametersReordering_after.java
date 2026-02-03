class C {
    void method(int i, String s) {
        method(s, 'a', i);
    }

    void method(String s, char c, int j) {
        System.out.println("i = " + j + " s = " + s);
    }
}

class C1 extends C {
    void method(String s, char c, int j) {
        System.out.println("i = " + j + " s = " + s);
    }
}

class Usage {
    {
        new C().method(1, null);
        new C1().method(1, null);
    }
}