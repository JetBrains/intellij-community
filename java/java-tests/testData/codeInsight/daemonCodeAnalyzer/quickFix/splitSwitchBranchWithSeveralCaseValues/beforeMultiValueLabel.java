// "Split values of 'switch' branch" "true-preview"
class C {
    void test(int i) {
        switch (i) {
            <caret>case 1, 2, 3:
                System.out.println("hello");
                break;
            case 4:
                System.out.println("bye");
        }
    }
}