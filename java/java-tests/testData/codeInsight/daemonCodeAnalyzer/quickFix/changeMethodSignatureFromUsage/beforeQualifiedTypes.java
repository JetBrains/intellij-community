// "Add 'Inner1' as 1st parameter to method 'Inner2'" "true"
class CoolTest {
class Inner1 {}

void method() {
new Inner2(new In<caret>ner1());
}

class Inner2 {
Inner2() { }
}
}