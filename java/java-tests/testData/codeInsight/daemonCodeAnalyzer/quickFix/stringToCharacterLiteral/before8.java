// "Change "1" to '1' (to char literal)" "true-preview"
class Simple {
    Simple(int i) {}
    Simple(char ch) {}
}

class Descendant extends Simple {
    Descendant() {super(<caret>"1");}
}
