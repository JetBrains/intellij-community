// "Transform body to single exit-point form" "true-preview"
class Test {
    private <caret>String nameByIndex(int colourIndex) {
        if (colourIndex == 1) {
            return "Red";
        } else if (colourIndex == 2) {
            return "Green";
        }
        return "Blue";
    }
}