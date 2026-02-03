// "Remove switch label '2'" "true-preview"
class Main {
    void test(Integer i) {
        switch (i) {
            case 1, 3:
                System.out.println("A");
                break;
            case 2:
                System.out.println("B");
        }
    }
}