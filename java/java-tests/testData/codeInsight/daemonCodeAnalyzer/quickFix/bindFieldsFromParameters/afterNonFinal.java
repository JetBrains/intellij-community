// "Bind constructor parameters to fields" "true-preview"

class Xerxes {
    private int myValue;

    Xerxes(String s) {
    }
    <caret>Xerxes(int value) {
        myValue = value;
    }
}