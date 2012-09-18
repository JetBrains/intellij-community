interface Base {
}

interface I2 extends Base {
    void foo<caret>() default {
        System.out.println("Hi there.");
    }
}