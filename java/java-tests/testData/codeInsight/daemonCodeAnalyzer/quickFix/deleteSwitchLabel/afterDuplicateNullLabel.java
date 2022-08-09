// "Remove switch label 'null'" "true-preview"
class Main {
    void test(Integer i) {
        switch (i) {
            case 1, 2:
                System.out.println("A");
                break;
            case null:
                System.out.println("B");
        }
    }
}