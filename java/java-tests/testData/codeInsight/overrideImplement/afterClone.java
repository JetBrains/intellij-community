class Gen {
    protected Gen clone() {
        return null;
    }
}

class X2 extends Gen {
    @Override
    protected Gen clone() {
        <caret><selection>return super.clone();</selection>
    }
}