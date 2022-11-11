// "Split values of 'switch' branch" "true-preview"
class C {
    void test(int i) {
        switch (i) {
            case 1:
                System.out.println("hello");
                break;
            case 2:
                System.out.println("hello");
                break;
            case 3:
                System.out.println("hello");
                break;
            case 4:
                System.out.println("bye");
        }
    }
}