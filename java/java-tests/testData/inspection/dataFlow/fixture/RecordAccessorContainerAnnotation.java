import org.eclipse.jdt.annotation.*;

@NonNullByDefault
record Record(String name){}

class Test {
  void test(Record r) {
    if (<warning descr="Condition 'r.name() != null' is always 'true'">r.name() != null</warning>) {
    }
  }
}