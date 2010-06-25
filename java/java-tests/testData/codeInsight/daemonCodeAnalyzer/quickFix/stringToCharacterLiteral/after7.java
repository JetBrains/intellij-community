// "Change '1' to "1" (to String literal)" "true"
class Simple {
    Simple() {this(<caret>"1");}
    Simple(String s) {}
}
