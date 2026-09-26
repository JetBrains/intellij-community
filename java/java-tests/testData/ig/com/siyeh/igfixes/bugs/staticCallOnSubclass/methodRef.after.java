
class I {
    static <Z> void FOO() {

    }
}

class A extends I  {
    {
        Runnable r = I/*c2*/::<String>FOO;
    }
}

