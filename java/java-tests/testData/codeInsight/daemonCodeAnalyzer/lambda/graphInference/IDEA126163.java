class Test {

  public static void main( String[] args ) throws Exception {
    Checker.assertThat("", Utils.is(Utils.notNullValue()));
  }
}

interface Util<T> {
}

class Utils {
  static <T> Util<T> is( Util<T> util ) {
    return null;
  }

  static <T> Util<T> is( T t ) {
    return null;
  }

  static <T> Util<T> notNullValue() {
    return null;
  }
}

class Checker {
  static <T> void assertThat(T actual, Util<T> util) {
  }
}

