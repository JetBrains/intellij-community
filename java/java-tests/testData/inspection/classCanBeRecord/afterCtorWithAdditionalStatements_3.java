// "Convert to record class" "true-preview"

record Main(int a, int b) {

    Main(int a) {
        this(a, 0);
        {
            System.out.println("Some random code block");
        }
        for (int i = 0; i < 42; i++) {
            System.out.println("i is " + i);
        }
    }
}
