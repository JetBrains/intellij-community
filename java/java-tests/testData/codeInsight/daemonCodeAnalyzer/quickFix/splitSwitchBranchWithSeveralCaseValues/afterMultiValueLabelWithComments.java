// "Split values of 'switch' branch" "true-preview"
class C {
    void test(int i) {
        switch (i) {
            // 1
            case /*2*/1 /*3*/ /*4*/: //5
                // 6
                System.out.println("hello");
                break;
            case 2:
                // 6
                System.out.println("hello");
                break;
            case 3:
                // 6
                System.out.println("hello");
                break;
            // 7
            case 4:
                System.out.println("bye");
        }
    }
}