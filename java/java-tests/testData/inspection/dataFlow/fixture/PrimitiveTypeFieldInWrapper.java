public class PrimitiveTypeFieldInWrapper {
  void foo() {
    boolean B = <warning descr="Condition 'Byte.TYPE == byte.class' is always 'true'">Byte.TYPE == byte.class</warning>;
    boolean C = <warning descr="Condition 'Character.TYPE == char.class' is always 'true'">Character.TYPE == char.class</warning>;
    boolean D = <warning descr="Condition 'Double.TYPE == double.class' is always 'true'">Double.TYPE == double.class</warning>;
    boolean F = <warning descr="Condition 'Float.TYPE == float.class' is always 'true'">Float.TYPE == float.class</warning>;
    boolean I = <warning descr="Condition 'Integer.TYPE == int.class' is always 'true'">Integer.TYPE == int.class</warning>;
    boolean J = <warning descr="Condition 'Long.TYPE == long.class' is always 'true'">Long.TYPE == long.class</warning>;
    boolean S = <warning descr="Condition 'Short.TYPE == short.class' is always 'true'">Short.TYPE == short.class</warning>;
    boolean Z = <warning descr="Condition 'Boolean.TYPE == boolean.class' is always 'true'">Boolean.TYPE == boolean.class</warning>;
    boolean V = <warning descr="Condition 'Void.TYPE == void.class' is always 'true'">Void.TYPE == void.class</warning>;
  }
}