class ParentCtor {
    public ParentCtor(String s) {
    }
}

class <caret>ChildCtor extends ParentCtor {
    private static final String CONST = "";

    public ChildCtor() {
        super(CONST);
    }
}

class Usage {
    public void test() {
        ChildCtor c = new ChildCtor();
    }
}