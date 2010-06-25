public class X  {
    String <caret>l;

    public X() {
        fs("oo", this);
    }

    void fs(String t, X x)
    {
        x.set(t);
    }
                //
    void set(String d) {
        l = d;
    }
}

class XX extends X {
    void fs(String t, X x) {
        x.fs(t, x);
    }
}

