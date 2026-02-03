// "Rename reference" "false"
class Foo {
    void bar(int x) {
    }

    void buzz() {
        b<caret>ar(); // Try to rename bar with Shift+F6
    }
}