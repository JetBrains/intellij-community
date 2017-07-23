class ListA<Ta>  {
    public <R extends Ta> Ta foo() { throw new Error(); }
}

class ListB<Tb> extends ListA<Tb> {
    public <Rb extends Tb> Rb foo() { throw new Error(); }
}

class ListC<Tc> extends ListB<Tc> {
}