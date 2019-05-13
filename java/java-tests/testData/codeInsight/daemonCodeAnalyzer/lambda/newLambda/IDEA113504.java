class BugExample {

    public static void main(String... args) {
        Observable<String> obs = create((Observer<? super String> o) -> {
            o.onNext("one");
            o.onNext("two");
            o.onCompleted();
        });

        obs.subscribe(new Observer<String>() {
            @Override
            public void onCompleted() {
                System.out.println("Completed");
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("Error");
            }

            @Override
            public void onNext(String v) {
                System.out.println("Value: " + v);
            }
        });

        Observable<String> obs2 = create(new OnSubscribeFunc<String>() {
            @Override
            public void onSubscribe(Observer<? super String> o) {
                o.onNext("one");
                o.onNext("two");
                o.onCompleted();
            }
        });

        obs2.subscribe(new Observer<String>() {
            @Override
            public void onCompleted() {
                System.out.println("Completed");
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("Error");
            }

            @Override
            public void onNext(String v) {
                System.out.println("Value: " + v);
            }
        });

    }

    public static class Observable<T> {
        private final OnSubscribeFunc<T> f;

        public Observable(OnSubscribeFunc<T> f) {
            this.f = f;
        }

        public void subscribe(Observer<T> o) {
            f.onSubscribe(o);
        }
    }

    public static <T> Observable<T> create(OnSubscribeFunc<T> func) {
        return new Observable<T>(func);
    }

    public static interface OnSubscribeFunc<T> {

        public void onSubscribe(Observer<? super T> t1);

    }

    public interface Observer<T> {

        public void onCompleted();

        public void onError(Throwable e);

        public void onNext(T args);
    }

}
