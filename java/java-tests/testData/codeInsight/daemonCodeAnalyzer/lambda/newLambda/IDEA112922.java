class CyclicInference {

    interface Execute {
        void execute();
    }

    /**
     * Lambda wrapper for expression-like lambdas
     *
     * @param lambda
     * @param <I> interface which lambda class implements (derived by compiler via type inference)
     * @param <T> lambda class having function code to be executed
     * @return I interface implemented by lambda
     */
    private static <I, T extends I> I lambdaWrapper(final T lambda) {
        return (I)lambda;
    }

    /**
     * How expression-like lambdas returning void can be wrapped
     */
    public void lambdaWithOneExpressionReturningVoid() {
        Execute sayHello = lambdaWrapper(() -> System.out.println("Hello"));
        sayHello.execute();
    }

    public static void main(String[] args) {
        CyclicInference lam = new CyclicInference();
        lam.lambdaWithOneExpressionReturningVoid();
    }
}
