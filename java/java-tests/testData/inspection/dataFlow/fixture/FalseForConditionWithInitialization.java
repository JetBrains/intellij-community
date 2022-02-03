class Test {

    public static void test() {
        for(int i = <error descr="Cannot resolve method 'launchMissiles' in 'Test'">launchMissiles</error>(); (false);) {
          System.out.println("Hello");
        }
        boolean c = false;
        for(int i = <error descr="Cannot resolve method 'launchMissiles' in 'Test'">launchMissiles</error>(); (<warning descr="Condition 'c' is always 'false'"><caret>c</warning>);) {
            System.out.println("Hello");
        }
        int i = 1;
    }
}