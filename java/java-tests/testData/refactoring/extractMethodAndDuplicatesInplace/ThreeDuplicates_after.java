class Test {
    void test1(){
        sayHello("Hello!", "user");
    }

    private static void sayHello(String s, String user) {
        System.out.println(s);
        System.out.println(user);
    }

    void test2(String name){
        sayHello("Hello!", name);
    }

    void test3(){
        sayHello("Good morning!", "user");
    }
}