interface Base {
    void foo<caret>() default {
        System.out.println("Hi there.");
    }
}

interface I2 extends Base {
}