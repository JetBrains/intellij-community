class Super {
    public static final Super FOO = null;
    public static final boolean FOX = true;
}

class Intermediate {
    Super s = Super.FOO<caret>
}


