
class Bug {
    Bug(String s) {}

    void m(String s) {
        final String str = s.substring(1);
        new Bug(str) {
            @Override
            public String toString() {
                return str;
            }
        };
    }
}