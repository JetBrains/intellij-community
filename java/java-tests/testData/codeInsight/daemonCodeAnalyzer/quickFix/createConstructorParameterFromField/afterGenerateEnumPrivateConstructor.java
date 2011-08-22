// "Add constructor parameter" "true"
public enum MyEnum
{
  VALUE_ONE(myField);
  private final int myField;

    private MyEnum(int myField) {
        this.myField = myField;
    }
}