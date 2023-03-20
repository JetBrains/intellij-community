// "Remove switch branch '2'" "true-preview"
class Main {
    void test(Integer i) {
        switch (i) {
            case 1, 2, 3:
                System.out.println("A");
                break;
            case <caret>2:
                System.out.println("B");
        }
    }
}