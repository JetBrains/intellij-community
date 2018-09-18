import org.jetbrains.annotations.Nullable;

abstract class Node {
  abstract @Nullable Node getParent();
  abstract @Nullable String getName();

  Node getRoot() {
    Node node = this;
    while(node.<warning descr="Method invocation 'getName' may produce 'NullPointerException'">getName</warning>() == null) {
      node = node.getParent();
    }
    return node;
  }
}

class GetUnknownTest {
  private void test(Message message, boolean isApplicable) {
    if (message == null && field == null) {
      return;
    }

    if (message != null) {
      field = message.getHeader();
      doSomething();
    }

    if (isApplicable) {
      // Dubious warning: message.getHeader() is not annotated, but assigned to nullable field; should we consider the result as nullable?
      System.out.println(field.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>());
    }
  }

  interface Message {
    Object getHeader();
  }

  @Nullable Object field;

  native void doSomething();
}
class BooleanTest {
  native private Object getSomething();
  private @Nullable Boolean field;

  private boolean test(Object x) {
    if (field == null) {
      field = x != null;

      if (getSomething() != null) {
        field = true;
      }
    }

    return field.booleanValue();
  }
}