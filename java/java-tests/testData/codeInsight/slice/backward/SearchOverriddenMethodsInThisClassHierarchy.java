interface I {
    String getValue();
}

abstract class X implements I {
    void foo() {
        String <caret>s = <flown1>getValue();
    }
}

class Y implements I {
    public String getValue() {
        return "Y";
    }
}

class Impl extends X {
    public String getValue() {
        return <flown11>"Impl";
    }
}