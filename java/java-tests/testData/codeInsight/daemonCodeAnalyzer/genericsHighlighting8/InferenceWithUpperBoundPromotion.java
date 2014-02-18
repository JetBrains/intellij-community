class NestedGenericGoodCodeIsRed {

    public void main( String[] args ) {
        satisfiesAllOf(isPositive(), isEqualTo(10.9));
        satisfiesAllOf(isPositive(), isEqualTo(10));

        Number num = null;
        satisfiesAllOf(isPositive(), isEqualTo(num));

        this.<Number>satisfiesAllOf(isPositive(), isEqualTo(10));
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
