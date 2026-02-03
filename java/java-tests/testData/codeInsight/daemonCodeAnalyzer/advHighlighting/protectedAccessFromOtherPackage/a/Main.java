package a;

import b.Son;

public class Main {
    public static void main(String[] args) {

        Son bad = new Son();
        bad.<error descr="'func()' has protected access in 'b.Son'">func</error>();

    }
}
