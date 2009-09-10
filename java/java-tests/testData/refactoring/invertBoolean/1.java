interface I {
    boolean <caret>isFoo();
}

class RRR implements I {
    public boolean isFoo() {
        return true;
    }

    {
        boolean foo = isFoo();
    }

    void g(I i) {
        boolean foo = i.isFoo();
    }
}
