import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class Scratch {
  String[] data;
  @Nullable String value;
  String value2;
  
  void test() {
    if (hasValue() && value.isEmpty()) {}
    if (!hasValue() && value.<warning descr="Method invocation 'isEmpty' will produce 'NullPointerException'">isEmpty</warning>()) {}
  }
  
  void testAndChainInlining() {
    if (hasData() && <warning descr="Condition 'data.length > 0' is always 'true' when reached">data.length > 0</warning>) {}
    if (<warning descr="Dereference of 'data' may produce 'NullPointerException'">data</warning>.length > 0 && <warning descr="Condition 'hasData()' is always 'true' when reached">hasData()</warning>) {}
  }
  
  void testOverriddenNullity() {
    // we inline nullable value, but should not report nullability problem here: it's reported inside the inlined method
    System.out.println(getValueNotNull().trim());
    // TODO: technically we should report here, but due to inlining, @Nullable part is lost
    System.out.println(getValue2Nullable().trim());
  }

  private boolean hasValue() {
    return value != null;
  }
  
  @Nullable
  private String getValue2Nullable() {
    return value2;
  }
  
  @NotNull
  private String getValueNotNull() {
    return <warning descr="Expression 'value' might evaluate to null but is returned by the method declared as @NotNull">value</warning>;
  }
  
  private boolean hasData() {
    return data != null && data.length > 0;
  }
  
  int[] array;

  private int readValue() {
    return array[0];
  }
  
  void testArraySize() {
    if(readValue() > 0 && <warning descr="Condition 'array.length > 0' is always 'true' when reached">array.length > 0</warning>) {}
  }
  
  Object element;
  
  private String getString() {
    return ((String)element);
  }
  
  void testGetString() {
    if(!getString().isEmpty() && <warning descr="Condition 'element instanceof String' is always 'true' when reached">element instanceof String</warning>) {}
  }
}