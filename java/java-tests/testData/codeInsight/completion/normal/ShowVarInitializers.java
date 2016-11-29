class X {
    {
        E.FIELD<caret>
    }
}

enum E {
    FIELD1( "x"),
    FIELD2("y") {
        public String toString() {
            return super.toString();
        }
    },
    FIELD3 {};

    E(String s) {
    }

    public static final int FIELD4 = 42;
}