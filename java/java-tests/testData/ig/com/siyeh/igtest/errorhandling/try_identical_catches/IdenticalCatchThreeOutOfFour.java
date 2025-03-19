class Main {
    static class Ex1 extends Exception {}
    static class Ex2 extends Exception {}
    static class Ex3 extends Ex1 {}

    public void test() {
        try {
            if(Math.random() > 0.5) {
                throw new Ex1();
            }
            if(Math.random() > 0.5) {
                throw new Ex2();
            }
            if(Math.random() > 0.5) {
                throw new Ex3();
            }
        }
        catch(RuntimeException ex) {
            ex.printStackTrace();
        }
        catch(Ex3 ignored) {
        }
        catch(Error e) {
            return;
        }
        <warning descr="'catch' branch identical to 'RuntimeException' branch">catch(Ex2 ex)</warning> {
            ex.printStackTrace();
        }
        <warning descr="'catch' branch identical to 'RuntimeException' branch">catch(Ex1 <caret>ex)</warning> {
            ex.printStackTrace();
        }
    }
}