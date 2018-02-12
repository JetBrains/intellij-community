import java.lang.annotation.*;

@Target(ElementType.TYPE_USE)
@interface TypeUseNotNull {}

enum TypeUseInEnumConstructor {
  Foo(null, "a", "b");

  TypeUseInEnumConstructor(String nuS, @TypeUseNotNull String nnS, @TypeUseNotNull String nnS2) {
  }

}