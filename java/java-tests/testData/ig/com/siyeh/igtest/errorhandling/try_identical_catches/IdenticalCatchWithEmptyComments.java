class C {
    void foo() {
        try {
            bar();
        } catch (Ex1 e) {
            // unique comment
        } catch (Ex2 e) {
        } <warning descr="'catch' branch identical to 'Ex2' branch">catch (Ex3 <caret>e)</warning> {
            //
            /*
            */
        }
    }

    void bar() throws Ex1, Ex2, Ex3 {}

    static class Ex1 extends Exception {}
    static class Ex2 extends Exception {}
    static class Ex3 extends Exception {}
}