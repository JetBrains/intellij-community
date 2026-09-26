class BlockStatement {

    void x() {
        foo();
        {
            <caret>goo();
        }
    }
}