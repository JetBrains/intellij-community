public class TrackRecordFields {
  record SimpleRecord(Integer a) {
  }

  record SimpleRecordWithInit(Integer a) {
    SimpleRecordWithInit {
      //some init
      System.out.println("1");
    }
  }

  record SimpleRecordWithConstructor(Integer a) {
    SimpleRecordWithConstructor(Integer a) {
      System.out.println("1");
      if (a == null) {
        this.a = 1;
      } else {
        this.a = a + 1;
      }
    }
  }

  record LongRecord(long a) {
  }

  record NestedRecord(SimpleRecord simpleRecord) {
  }

  record NestedObjectRecord(Object object) {
  }

  static void main() {
    checkSimple();
    checkNestedRecords();
    checkInstanceOf();
    checkSwitch();
    checkSwitchPrimitive();
  }

  private static void checkSwitchPrimitive() {

    NestedObjectRecord nestedObjectRecord = new NestedObjectRecord(new LongRecord(1));
    switch (nestedObjectRecord) {
      case <warning descr="Switch label 'NestedObjectRecord(LongRecord(int i))' is the only reachable in the whole switch">NestedObjectRecord(LongRecord(int i))</warning> -> {
        if (<warning descr="Condition 'i == 1' is always 'true'">i == 1</warning>) {
          System.out.println(1);
        }
        System.out.println(i);
      }
      default -> throw new IllegalStateException("Unexpected value: " + nestedObjectRecord);
    }

    NestedObjectRecord nestedObjectRecord2 = new NestedObjectRecord(new LongRecord(Long.MAX_VALUE));
    switch (nestedObjectRecord2) {
      case <warning descr="Switch label 'NestedObjectRecord(LongRecord(int i))' is unreachable">NestedObjectRecord(LongRecord(int i))</warning> -> {
        if (i == 1) {
          System.out.println(1);
        }
        System.out.println(i);
      }
      default -> throw new IllegalStateException("Unexpected value: " + nestedObjectRecord);
    }
  }


  private static void checkSwitch() {
    NestedObjectRecord nestedObjectRecord = new NestedObjectRecord(new SimpleRecord(1));
    switch (nestedObjectRecord) {
      case <warning descr="Switch label 'NestedObjectRecord(SimpleRecord(Integer i))' is the only reachable in the whole switch">NestedObjectRecord(SimpleRecord(Integer i))</warning> -> {
        if (<warning descr="Condition 'i == 1' is always 'true'">i == 1</warning>) {
          System.out.println(1);
        }
        System.out.println(i);
      }
      default -> throw new IllegalStateException("Unexpected value: " + nestedObjectRecord);
    }
  }

  private static void checkInstanceOf() {
    NestedObjectRecord nestedObjectRecord = new NestedObjectRecord(new SimpleRecord(1));
    if (<warning descr="Condition 'nestedObjectRecord instanceof NestedObjectRecord(SimpleRecord(Integer i)) && i == 1' is always 'true'"><warning descr="Condition 'nestedObjectRecord instanceof NestedObjectRecord(SimpleRecord(Integer i))' is always 'true'">nestedObjectRecord instanceof NestedObjectRecord(SimpleRecord(Integer i))</warning> &&
        <warning descr="Condition 'i == 1' is always 'true' when reached">i == 1</warning></warning>) {
      System.out.println("1");
    }

    NestedObjectRecord nestedObjectRecord2 = new NestedObjectRecord(new SimpleRecord(1));
    if (<warning descr="Condition 'nestedObjectRecord2 instanceof NestedObjectRecord(SimpleRecord(Integer i)) && i == 2' is always 'false'"><warning descr="Condition 'nestedObjectRecord2 instanceof NestedObjectRecord(SimpleRecord(Integer i))' is always 'true'">nestedObjectRecord2 instanceof NestedObjectRecord(SimpleRecord(Integer i))</warning> &&
        <warning descr="Condition 'i == 2' is always 'false' when reached">i == 2</warning></warning>) {
      System.out.println("2");
    }
  }

  private static void checkNestedRecords() {
    NestedRecord nestedRecord = new NestedRecord(new SimpleRecord(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>));
    if (<warning descr="Condition 'nestedRecord.simpleRecord.a == null' is always 'true'">nestedRecord.simpleRecord.a == null</warning>) {
      System.out.println("null");
    }

    NestedRecord nestedRecord2 = new NestedRecord(new SimpleRecord(1));
    if (<warning descr="Condition 'nestedRecord2.simpleRecord().a() == 1' is always 'true'">nestedRecord2.simpleRecord().a() == 1</warning>) {
      System.out.println("1");
    }
  }

  private static void checkSimple() {
    SimpleRecord simpleRecord = new SimpleRecord(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
    if (<warning descr="Condition 'simpleRecord.a == null' is always 'true'">simpleRecord.a == null</warning>) {
      System.out.println("null");
    }

    SimpleRecord simpleRecord2 = new SimpleRecord(1);
    if (<warning descr="Condition 'simpleRecord2.a() == 2' is always 'false'">simpleRecord2.a() == 2</warning>) {
      System.out.println("2");
    }

    SimpleRecordWithInit simpleRecordWithInit = new SimpleRecordWithInit(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
    if (simpleRecordWithInit.a == null) {
      System.out.println("null");
    }

    SimpleRecordWithConstructor simpleRecordWithConstructor = new SimpleRecordWithConstructor(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
    if (simpleRecordWithConstructor.a == null) {
      System.out.println("null");
    }
  }
}