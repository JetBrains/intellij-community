import org.jetbrains.annotations.*;

class Test {
      public void foo(@NotNull(exception = NullPointerException.class) String a) { }
      public void foo2(@NotNull(exception = <warning descr="Custom exception class should have a constructor with a single message parameter of String type">CustomException.class</warning>) String a) { }
}

class CustomException extends Exception {}