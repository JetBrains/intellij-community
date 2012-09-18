interface Base {
}

interface I2 extends Base {
    void foo() default {
        System.out.println("Hi there.");
    }
}