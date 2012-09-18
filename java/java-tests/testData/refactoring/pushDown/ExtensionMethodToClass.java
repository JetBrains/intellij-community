interface Base {
    void foo<caret>() default {
        System.out.println("Hi there.");
    }
}

class C implements Base {
}