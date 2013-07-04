import org.jetbrains.annotations.Nullable;

public class Goo {
  Permission permission;

  {
    Object category = permission.getCategory();
    System.out.println(<warning descr="Method invocation 'category.hashCode()' may produce 'java.lang.NullPointerException'">category.hashCode()</warning>);
  }
}

class Permission {
  Object category;


  @Nullable Object getCategory() {
    return category;
  }
}