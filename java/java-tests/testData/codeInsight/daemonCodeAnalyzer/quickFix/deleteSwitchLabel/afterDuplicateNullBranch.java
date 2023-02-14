// "Remove switch branch 'null'" "true-preview"
class Main {
    void test(Integer i) {
        switch (i) {
            case 1, null, 2:
                System.out.println("A");
                break;
        }
    }
}