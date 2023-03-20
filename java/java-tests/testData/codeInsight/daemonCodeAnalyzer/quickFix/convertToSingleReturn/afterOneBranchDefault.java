// "Transform body to single exit-point form" "true-preview"
class Test {
    private String nameByIndex(int colourIndex) {
        String result = "Blue";
        if (colourIndex == 1) {
            result = "Red";
        }
        return result;
    }
}