// "Add constructor parameter" "true"
public enum MyEnum
{
  VALUE_ONE(0);
  private final int myField;

    MyEnum(int myField) {
        this.myField = myField;
    }
}