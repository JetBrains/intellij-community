class ParentCtor {
    public static final String PARENT_CONST = "";

    public ParentCtor(String s) {
    }
}

class ChildCtor extends ParentCtor {
    public ChildCtor(boolean b) {
        super(PARENT_CONST);
    }
}

class Usage {
    public void test() {
        new <caret>ChildCtor(true);
    }
}
