// "Rename reference" "true-preview"
class Foo {

    void buzz() {
        buzz();
        this.buzz(); //don't pay attention to same type qualifier
        a.buzz();
    }
}