class Super {
    public static final Super FOO = null;
}

class Intermediate extends Super {

    Super s = FOO;<caret>
}


