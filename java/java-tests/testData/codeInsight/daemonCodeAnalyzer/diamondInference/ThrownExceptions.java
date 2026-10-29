import java.io.IOException;

class Throwing<T> {
  Throwing(T t) throws IOException { }
}

class Thrower<X extends Exception> {
  Thrower() throws X { }
}

class ClassThrower<T, X extends Exception> {
  ClassThrower(T t, Class<X> c) throws X { }
}

class ThrownExceptions {
  void unhandled() {
    new <error descr="Unhandled exception: java.io.IOException">Throwing<></error>("a");
    <error descr="Unhandled exception: java.io.IOException">new Throwing<>("a") { };</error>
    Thrower<IOException> t1 = new <error descr="Unhandled exception: java.io.IOException">Thrower<></error>();
    Thrower<RuntimeException> t2 = new Thrower<>();
    Thrower<? extends IOException> t3 = new <error descr="Unhandled exception: java.io.IOException">Thrower<></error>();
    new <error descr="Unhandled exception: java.io.IOException">ClassThrower<></error>("a", IOException.class);
    new ClassThrower<>("a", IllegalStateException.class);
  }

  void handled() throws IOException {
    new Throwing<>("a");
    new Throwing<>("a") { };
    Thrower<IOException> t = new Thrower<>();
    new ClassThrower<>("a", IOException.class);
  }
}
