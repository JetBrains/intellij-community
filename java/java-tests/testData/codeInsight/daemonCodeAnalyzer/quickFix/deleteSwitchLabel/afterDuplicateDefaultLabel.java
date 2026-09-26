// "Remove switch label 'default'" "true-preview"
class Main {
    void test(Integer i) {
        switch (i) {
            case 1, 2:
                System.out.println("A");
                break;
            default:
                System.out.println("B");
        }
    }
}