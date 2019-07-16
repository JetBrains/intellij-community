// "Transform body to single exit-point form" "true"
class Test {
    private <caret>String nameByIndex(int colourIndex) {
        if (colourIndex == 1) {
            return "Red";
        }
        return "Blue";
    }
}