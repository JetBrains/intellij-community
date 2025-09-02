// "Convert to record class" "true-preview"
record Point(double x, double y) {
    Point(double x, double y) {
        this.x = x;
        this.y = y;
        System.out.println("Hello I was just created");
    }
}
