interface Base {
    void foo() default {
        System.out.println("Hi there.");
    }
}

interface I2 extends Base {
}