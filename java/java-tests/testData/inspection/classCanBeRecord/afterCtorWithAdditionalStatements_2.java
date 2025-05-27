// "Convert to record class" "true-preview"

record Main(int a, int b) {

    Main(int a) {
        this(a, 0);
        if (a > 0) {
            System.out.println("A is positive");
        }
    }
}
