class X {

    void x() {
        new Object() {
            void x() {
                System.out.println(<selection>new <caret>Object()</selection>);
            }
        };
    }
}