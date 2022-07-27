// "Remove switch branch 'default'" "true-preview"
class Main {
    void test(Integer i) {
        switch (i) {
            case 1, 2, default:
                System.out.println("A");
                break;
        }
    }
}