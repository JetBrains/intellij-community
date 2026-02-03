import org.jetbrains.annotations.*;

// IDEA-227734
class Test {
  enum MyEnum{ A, B, C }

  @NotNull
  String doesNotShowWarning(@NotNull MyEnum myEnum){
    switch(myEnum){
      case A:
      case B:
      case C:
        return "YES";
      default: return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }

  @NotNull
  String doesNotShowWarning2(@NotNull final MyEnum myEnum){
    switch(myEnum){
      case A:
      case B:
      case C:
        return "YES";
    }
    return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
  }

  @NotNull
  String showsWarning(@NotNull MyEnum myEnum){
    switch(myEnum){
      case A:
      case B:
        return "YES";
      default: return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }
}