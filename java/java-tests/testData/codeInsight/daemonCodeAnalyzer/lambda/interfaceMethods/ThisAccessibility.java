interface Bazz {
    Bazz foo = <error descr="'Bazz.this' cannot be referenced from a static context">Bazz.this</error>;
    static void foo1() {
        Bazz foo = <error descr="'Bazz.this' cannot be referenced from a static context">Bazz.this</error>;
    }

    Runnable bar = new Runnable() {
       @Override
       public void run() {
           Bazz f = <error descr="'Bazz.this' cannot be referenced from a static context">Bazz.this</error>;
       }
    };
  
  
    default void foo() {
        Bazz foo = Bazz.this;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Bazz f = Bazz.this;
            }
        };
    }
}