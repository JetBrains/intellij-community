class X {
    static class Y<T> {
    }

    static <T> Y<T> x(Y<? super Y<T>> y) {
        return null;
    }


    {
      Y<Y<Long>> y1 = null;
      X.<ref>x(y1);
    }
}
}
