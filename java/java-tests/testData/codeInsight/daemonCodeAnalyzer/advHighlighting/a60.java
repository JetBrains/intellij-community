class Y {
    int size = 4;
}

class Z extends Y {
    class I {
        void foo() {
             System.out.println("size = " + <error descr="'Y' is not an enclosing class">Y.this</error>.size); // illegal construct
        }
    }
}

class R {
    public void smu() {
        System.out.println(<error descr="'Z' is not an enclosing class">Z.super</error>.toString());
    }
}
