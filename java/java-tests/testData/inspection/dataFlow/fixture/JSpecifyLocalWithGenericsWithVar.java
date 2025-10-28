import org.jspecify.annotations.*;

import java.util.List;

@NullMarked
class Scratch {
  void exact(ExactFactory factory) {
    var created = factory.create();
    System.out.println("Created: " + created);
    System.out.println("Created: " + created.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>());
  }

  <T> void exact(GenericFactory<T> factory) {
    var created = factory.create();
    System.out.println("Created: " + created);
    System.out.println("Created: " + created.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>());
  }

  <T> void exact(GenericFactoryList<T> factory) {
    for (var o : factory.createList()) {
      if (o.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>() == 1) {
        System.out.println("null");
      }
      System.out.println("Created: " + o);
    }
  }

  <T> void polyNullNotNull(PolyNullGenericFactory<T> factory) {
    var created = factory.create();
    System.out.println("Created: " + created);
    System.out.println("Created: " + created.hashCode());
  }

  <T extends @Nullable Object> void polyNullNullable(PolyNullGenericFactory<T> factory) {
    var created = factory.create();
    System.out.println("Created: " + created);
    System.out.println("Created: " + created.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>());
  }

  @FunctionalInterface
  interface ExactFactory {
    @Nullable String create();
  }

  @FunctionalInterface
  interface GenericFactory<T> {
    @Nullable T create();
  }

  @FunctionalInterface
  interface GenericFactoryList<T> {
    List<@Nullable T> createList();
  }

  @FunctionalInterface
  interface PolyNullGenericFactory<T extends @Nullable Object> {
    T create();
  }
}