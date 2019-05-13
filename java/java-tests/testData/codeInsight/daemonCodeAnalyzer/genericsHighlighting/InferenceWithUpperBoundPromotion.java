class NestedGenericGoodCodeIsRed {

    public void main( String[] args ) {
        satisfiesAllOf(isPositive(), isEqualTo(10.9));
        satisfiesAllOf(isPositive(), isEqualTo(10));

        Number num = null;
        satisfiesAllOf(isPositive(), isEqualTo(num));

        this.<Number>satisfiesAllOf(isPositive(), <error descr="'satisfiesAllOf(NestedGenericGoodCodeIsRed.Predicate<? super java.lang.Number>, NestedGenericGoodCodeIsRed.Predicate<? super java.lang.Number>)' in 'NestedGenericGoodCodeIsRed' cannot be applied to '(NestedGenericGoodCodeIsRed.Predicate<java.lang.Number>, NestedGenericGoodCodeIsRed.Predicate<java.lang.Integer>)'">isEqualTo(10)</error>);
    }


    public interface Predicate<T> {

    }

    public  <ALL> void satisfiesAllOf( Predicate<? super ALL> first, Predicate<? super ALL> second ) {
    }

    public  <POSITIVE extends Number> Predicate<POSITIVE> isPositive() {
        return null;
    }

    public <EQUALTO extends Number> Predicate<EQUALTO> isEqualTo( EQUALTO target ) {
        return null;
    }

}