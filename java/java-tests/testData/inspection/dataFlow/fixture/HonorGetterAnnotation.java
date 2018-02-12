import org.jetbrains.annotations.Nullable;

class Goo {
  Permission permission;

  void test() {
    Object category = permission.getCategory();
    System.out.println(category.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
  }
}

class Permission {
  Object category;


  @Nullable Object getCategory() {
    return category;
  }
}