import org.jetbrains.annotations.Nullable;

enum MyEnum {
  ITEM,
  ANOTHER_ITEM,
  LOL_ITEM;
}

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "unused"})
class Main {
  void foo(@Nullable MyEnum myEnum) {
    if (myEnum == null) return;
    switch (myEnum) {
      case ITEM:
      case ANOTHER_ITEM:
        System.out.println(myEnum == MyEnum.ITEM ? "item" : "another");
        myEnum.name();
      default:
    }
  }
}