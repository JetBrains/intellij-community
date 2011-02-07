interface I {
    Object clone();
}

interface J extends I {
}

class CBase implements I {
}

class C extends CBase implements J {
    {
        super.<ref>clone();
    }
}