interface I {
    void m(int x);
}

interface I1 {
    int m();
}
class CastInContexts {
    void m(I s) { }

    void assignment() {
        I i1 = (I)(x-> { System.out.println(); });
        I i2 = (I)((x-> { System.out.println(); }));
    }
    
    void method() {
        m((I)(x-> { System.out.println(); }));
    }

    I returnContext() {
        return (I)(x -> { System.out.println(); });
    }

    {
      int i = <error descr="Inconvertible types; cannot cast '<lambda expression>' to 'int'">(int) ()-> 1</error>;
    }
}