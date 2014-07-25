class Test1 {
  interface Extractor<T, W> {
    Option<W> unapply(T t);
  }

  public static abstract class Option<T> {
    private static class None<T> extends Option<T> {}

    private static final Option NONE = new None();

    public static <T> Option<T> none() {
      return NONE;
    }

    public static <T> Option<T> option(T value) {
      if (value == null) {
        return NONE;
      } else {
        return null;
      }
    }
  }



  public static void main(String[] args) {
    Extractor<String, Integer> e0 = s -> s.equals("1") ? Option.option(1) : Option.none();
    Extractor<String, Integer> e = s -> {
      if (s.equals("1")) {
        return Option.option(1);
      } else {
        return Option.none();
      }
    };

    Extractor<String, Integer> e1 = s -> {
      if (s.equals("1")) {
        return Option.option(1);
      } else {
        return Option.option<error descr="'option(T)' in 'Test1.Option' cannot be applied to '(java.lang.String)'">("2")</error>;
      }
    };
  }
}

class Test2 {
  interface X<T extends Number> {
    T foo();
  }

  {
    X<?> x = () -> 123;
    X<? extends Number> x1 = () -> 123;
    
  }
}

class Test3 {
  interface X<T> {
    T foo();
  }

  {
    X<?> x = () -> 123;
  }
}