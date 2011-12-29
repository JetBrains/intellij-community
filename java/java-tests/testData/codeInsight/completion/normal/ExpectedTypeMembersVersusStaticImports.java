import static Super.FOO;

class Super {
    public static final Super FOO = null;
    public static final Super FOX = true;
}

class Intermediate {
    Super s = FO<caret>
}


