import java.util.List;

class Main {

  void genericTest(RecordWithGeneric<InnerRecord> record) {
    for (RecordWithGeneric(InnerRecord t  ) : List.of(record)) {
      System.out.println(t);
    }

    List<RecordWithGeneric<InnerRecord>> lists = List.of(record);
    for (RecordWithGeneric(InnerRecord t) : lists) {
      System.out.println(t);
    }
  }

  record RecordWithGeneric<T>(T t) {}
  record InnerRecord(int x) {}

  void test1(List<Upper> list) {
    for (<error descr="Pattern 'Main.Record' is not exhaustive on 'Main.Upper'">Record(var x)</error> : list) {}
  }

  void test2(List<UpperWithPermit> list) {
    for (PermittedRecord(var x) : list) {}
  }

  void test3(List<? extends PermittedRecord> list) {
    for (PermittedRecord(var x) : list ) {}
  }

  void test4(List<? extends UpperWithPermit>  list) {
    for (PermittedRecord(var x) : list ) {}
  }

  <T extends UpperWithPermit> void test5(List<T>  list) {
    for (PermittedRecord(var x) : list ) {}
    T t = list.get(0);
    PermittedRecord r = (PermittedRecord) t;
  }

  void test6(List<? super UpperWithPermit> list) {
    for (<error descr="Pattern 'Main.PermittedRecord' is not exhaustive on 'capture<? super Main.UpperWithPermit>'">PermittedRecord(var x)</error> : list) {}
  }

  void test7(List<PermittedRecord> list) {
    for (PermittedRecord(var x) : list) {}
  }

  void test8(List<? super PermittedRecord> list) {
    for (<error descr="Pattern 'Main.PermittedRecord' is not exhaustive on 'capture<? super Main.PermittedRecord>'">PermittedRecord(var x)</error> : list) {}
  }

  void test9(List list) {
    for (<error descr="Pattern 'Main.PermittedRecord' is not exhaustive on 'java.lang.Object'">PermittedRecord(var x)</error> : list) {}
  }

  void test10(List<?> list) {
    for (<error descr="Pattern 'Main.PermittedRecord' is not exhaustive on 'capture<?>'">PermittedRecord(var x)</error> : list) {}
  }

  void test11(List<Record> list) {
    for (Record(var x) : list) {}
  }

  sealed interface UpperWithPermit permits PermittedRecord {}
  record PermittedRecord(int x) implements UpperWithPermit {}
  interface Upper {}
  record Record(List<String> x) implements Upper {}
}
