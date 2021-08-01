// "Remove switch label 'null'" "true"
class Main {
    void test(Integer i) {
        switch (i) {
            case 1, <caret>null, 2:
                System.out.println("A");
                break;
            case null:
                System.out.println("B");
        }
    }
}