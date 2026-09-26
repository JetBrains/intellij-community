// "Split values of 'switch' branch" "true-preview"
class C {
    void test(int i) {
        switch (i) {
            // 1
            <caret>case /*2*/1, /*3*/2, /*4*/3: //5
                // 6
                System.out.println("hello");
                break;
            // 7
            case 4:
                System.out.println("bye");
        }
    }
}