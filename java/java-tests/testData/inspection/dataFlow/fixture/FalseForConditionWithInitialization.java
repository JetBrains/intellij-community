class Test {

    public static void test() {
        for(int i = <error descr="Cannot resolve method 'launchMissiles()'">launchMissiles</error>(); (<warning descr="Condition is always false">fa<caret>lse</warning>);) {
            System.out.println("Hello");
        }
        int i = 1;
    }
}