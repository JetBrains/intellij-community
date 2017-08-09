// "Rename reference" "true"
class Foo {

    void buzz() {
        b<caret>ar();
        this.bar(); //don't pay attention to same type qualifier
        a.bar();
    }
}