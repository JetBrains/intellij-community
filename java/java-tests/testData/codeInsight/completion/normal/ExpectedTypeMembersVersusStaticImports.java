import static Super.XFOO;

class Super {
    public static final Super XFOO = null;
    public static final Super XFOX = true;
}

class Intermediate {
    Super s = XFO<caret>
}


