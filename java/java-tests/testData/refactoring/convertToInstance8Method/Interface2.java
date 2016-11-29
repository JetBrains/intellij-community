interface I {
}

class C implements I {
}

interface J extends I {
}

class D extends C implements J {
}

class X {
    static void <caret>m(I i) {
    }
}