class SameName {
    void method1() {
        class Local {}
    }

    void method2() {
        class <caret>Local {}
    }
}
