// "Split values of 'switch' branch" "true"
class C {
    void test(int i) {
        switch (i) {
            // 1
            case /*2*/1<caret>, /*3*/2: // 4
                // 5
                System.out.println("hello");
                break;
                /*6*/
        }
    }
}