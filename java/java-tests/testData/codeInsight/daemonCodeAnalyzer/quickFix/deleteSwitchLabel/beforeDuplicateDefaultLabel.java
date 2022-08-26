// "Remove switch label 'default'" "true-preview"
class Main {
    void test(Integer i) {
        switch (i) {
            case 1, 2, <caret>default:
                System.out.println("A");
                break;
            case default:
                System.out.println("B");
        }
    }
}