interface Func<TIn, TOut>{
    TOut run(TIn in);
}


class Main {

    public static void main(final String[] args) {
        Func<Integer, String> func =  Integer::toString;
        System.out.println(func.run(6));
    }
}