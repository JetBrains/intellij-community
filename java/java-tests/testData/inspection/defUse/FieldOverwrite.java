public class FieldOverwrite {
  int val;
  int val2;
  int[] data;

  public FieldOverwrite(int field) {
    <warning descr="The value '123' assigned to 'this.val' is never used">this.val</warning> = 123;
    this.val = field;
  }

  void testArray() {
    data = new int[] {10, 20, 30, 40};
  }

  void twiceAssignment() {
    val = <warning descr="The value '1' assigned to 'val' is never used">val</warning> = 1;
    val2 = <warning descr="The value '2' assigned to 'val2' is never used">val2</warning> += 2;
  }

  void increment() {
    <warning descr="The value changed at 'val++' is never used">val++</warning>;
    val=2;
    <warning descr="The value '3' assigned to 'val' is never used">val</warning>+=3;
    val=4;
  }

  void use() {
    val = 1;
    val = calc(2);
  }
  
  void branches(boolean b) {
    val = 1;
    if (b) {
      val = 2;
    }
  }

  void branches2(boolean b) {
    <warning descr="The value '1' assigned to 'val' is never used">val</warning> = 1;
    if (b) {
      val = 2;
    } else {
      val = 3;
    }
  }
  
  void diamondBranches(boolean b) {
    if (b) {
      <warning descr="The value '2' assigned to 'val' is never used">val</warning> = 2;
    } else {
      <warning descr="The value '2' assigned to 'val2' is never used">val2</warning> = 2;
    }
    if (b) {
      val = 3;
    } else {
      val2 = 3;
    }
  }

  void diamondBranchesOk(boolean b) {
    if (b) {
      val = 2;
    } else {
      val2 = 2;
    }
    if (!b) {
      val = 3;
    } else {
      val2 = 3;
    }
  }

  int calc(int x) {
    return val * x;
  }

  private int getVal() {
    return val;
  }

  void noUseInlining() {
    <warning descr="The value '1' assigned to 'val2' is never used">val2</warning> = 1;
    val2 = getVal();
  }

  void test(FieldOverwrite fo) {
    <warning descr="The value '1' assigned to 'val' is never used">val</warning> = 1;
    val = 2;
    <warning descr="The value '3' assigned to 'fo.val' is never used">fo.val</warning> = 3;
    fo.val = 4;
  }

  // IDEA-195460
  private int intField;
  private static int intStaticField;

  public void main() {
    int intVar = 0;
    intVar = <warning descr="The value changed at 'intVar++' is never used">intVar++</warning>;
    System.out.println(intVar);

    intField = 0;
    intField = <warning descr="The value changed at 'intField++' is never used">intField++</warning>;
    System.out.println(intField);


    intStaticField = 0;
    intStaticField = <warning descr="The value changed at 'intStaticField++' is never used">intStaticField++</warning>;
    System.out.println(intStaticField);
  }

  void testUseStatic() {
    intStaticField = 1;
    useStatic();
    intStaticField = 2;
  }

  static void useStatic() {
    System.out.println(intStaticField);
  }
}