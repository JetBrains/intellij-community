
class Bug {
    Bug(String s) {}

    void m(String s) {
        new Bug(s.substring(1)) {
            @Override
            public String toString() {
                return <selection>s.substring(1)</selection>;
            }
        };
    }
}