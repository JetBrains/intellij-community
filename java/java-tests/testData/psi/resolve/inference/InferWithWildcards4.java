class X {
    static class Y<T> {
    }

    static <T> Y<T> x(Y<? super T[]> y) {
        return null;
    }


    {
      Y<Long[]> y1 = null;
      X.<ref>x(y1);
    }
}
}
