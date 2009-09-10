interface I {
    boolean isFoo();
}

class RRR implements I {
    public boolean isFooInverted() {
        return false;
    }

    {
        boolean foo = !isFooInverted();
    }

    void g(I i) {
        boolean foo = i.isFoo();
    }
}
