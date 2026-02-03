public class DuplicateWithAnonymousMethodReference {

    void test(){
        Runnable task = new Runnable() {
            @Override
            public void run() {
                <selection>local();
                System.out.println();</selection>

                local();
                System.out.println();
            }

            public void local() {
            }
        };
    }
}
