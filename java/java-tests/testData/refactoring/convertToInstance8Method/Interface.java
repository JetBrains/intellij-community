interface I {
}

interface J extends I {
}

class IImpl implements I {
}

class JImpl implements J {
}

class X {
    static void <caret>method(int i, I intf) {
        System.out.println("i = " + i + ", intf = " + intf);
    }

    {
        J j = new JImpl();
        method(0, j);
    }
}