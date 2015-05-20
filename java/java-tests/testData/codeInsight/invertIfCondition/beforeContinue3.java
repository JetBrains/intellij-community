// "Invert 'if' condition" "true"
class B {
    public static void foo() {
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                <caret>if (i == 0) {
                    System.out.println("== 0");
                    continue;
                }

                System.out.println("!= 0");
                continue;
            }

            System.out.println("i = " + i);
        }
    }
}