import typeUse.*;

class Test {
  private byte <warning descr="Not-null fields must be initialized">@NotNull</warning> [] field;
  private byte @NotNull [] initField;
  
  {
    initField = new byte[0];
  }
}