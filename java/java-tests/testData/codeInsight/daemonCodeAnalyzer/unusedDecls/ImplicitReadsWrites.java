class <warning descr="Class 'Test' is never used">Test</warning>{
  
  private int <warning descr="Private field 'fieldWritten' is assigned but never accessed">fieldWritten</warning>;

  private int fieldWritten2;
  
  int fieldReadWritten;
  
  int <warning descr="Package-private field 'fieldWritten3' is assigned but never accessed">fieldWritten3</warning><EOLError descr="';' expected"></EOLError>

  @Override
  public int hashCode() {
    return fieldWritten2;
  }
}