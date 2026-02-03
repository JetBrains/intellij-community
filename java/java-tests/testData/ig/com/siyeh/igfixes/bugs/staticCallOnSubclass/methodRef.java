
class I {
    static <Z> void FOO() {

    }
}

class A extends I  {
    {
        Runnable r = A/*c2*/::<String>F<caret>OO;
    }
}

