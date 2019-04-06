// "Split values of 'switch' branch" "true"
class C {
    void test(int i) {
        switch (i) {
            case 1, <caret>2:
                System.out.println("hello");
                break;
        }
    }
}