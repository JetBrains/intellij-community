import org.jetbrains.annotations.NotNull;

class ExtendParent{
  public void a(@NotNull String a){}
}

interface InterfaceImpl<T extends  Object>{
  void a(T a);
}

class A extends ExtendParent implements InterfaceImpl<@NotNull String>{}