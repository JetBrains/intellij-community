// "Change '1' to "1" (to String literal)" "true-preview"
class Simple {
    Simple() {this(<caret>"1");}
    Simple(String s) {}
}
