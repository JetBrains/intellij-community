// "Remove redundant initializer" "true"
class A {
void testFor() {
        if (true) {
            read();
            for(int i;; i++) {
              i = 10;
              System.out.println("Hello!");
            }
        }

    }

    private static int read() {
        System.out.println();
        return 0;
    }}