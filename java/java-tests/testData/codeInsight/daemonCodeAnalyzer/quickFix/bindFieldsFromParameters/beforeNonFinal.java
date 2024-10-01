// "Bind constructor parameters to fields" "true-preview"

class Xerxes {
    Xerxes(String s) {
    }
    <caret>Xerxes(int value) {
    }
}