// "Add constructor parameter" "true"
public enum MyEnum
{
  VALUE_ONE(myField);
  private final int myField;

    MyEnum(int myField) {
        this.myField = myField;
    }
}